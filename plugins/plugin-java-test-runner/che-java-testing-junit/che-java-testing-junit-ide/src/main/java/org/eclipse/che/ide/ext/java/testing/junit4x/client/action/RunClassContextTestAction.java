/*******************************************************************************
 * Copyright (c) 2012-2016 Codenvy, S.A.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *   Codenvy, S.A. - initial API and implementation
 *******************************************************************************/
package org.eclipse.che.ide.ext.java.testing.junit4x.client.action;

import com.google.inject.Inject;
import org.eclipse.che.api.workspace.shared.dto.ProjectConfigDto;
import org.eclipse.che.ide.api.action.AbstractPerspectiveAction;
import org.eclipse.che.ide.api.action.ActionEvent;
import org.eclipse.che.ide.api.app.AppContext;
import org.eclipse.che.ide.api.editor.EditorAgent;
import org.eclipse.che.ide.api.editor.EditorInput;
import org.eclipse.che.ide.api.editor.EditorPartPresenter;
import org.eclipse.che.ide.api.filetypes.FileTypeRegistry;
import org.eclipse.che.ide.api.notification.NotificationManager;
import org.eclipse.che.ide.api.notification.StatusNotification;
//import org.eclipse.che.ide.api.project.node.resource.SupportRename;
import org.eclipse.che.ide.api.resources.File;
import org.eclipse.che.ide.api.resources.Project;
import org.eclipse.che.ide.api.resources.VirtualFile;
import org.eclipse.che.ide.api.selection.Selection;
import org.eclipse.che.ide.api.selection.SelectionAgent;
import org.eclipse.che.ide.ext.java.client.action.JavaEditorAction;
//import org.eclipse.che.ide.ext.java.client.project.node.JavaFileNode;
import org.eclipse.che.ide.ext.java.client.projecttree.JavaSourceFolderUtil;
import org.eclipse.che.ide.ext.java.client.util.JavaUtil;
import org.eclipse.che.ide.ext.java.testing.core.client.TestServiceClient;
import org.eclipse.che.ide.ext.java.testing.junit4x.client.JUnitTestLocalizationConstant;
import org.eclipse.che.ide.ext.java.testing.junit4x.client.JUnitTestResources;
import org.eclipse.che.ide.ext.java.testing.core.client.view.TestResultPresenter;
import org.eclipse.che.ide.ext.java.testing.core.shared.TestResult;
import org.eclipse.che.ide.resources.tree.FileNode;
import org.eclipse.che.ide.rest.DtoUnmarshallerFactory;
import org.eclipse.che.ide.util.loging.Log;
import org.eclipse.che.ide.websocket.rest.RequestCallback;
import org.eclipse.che.ide.websocket.rest.Unmarshallable;

import javax.validation.constraints.NotNull;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

import static org.eclipse.che.ide.api.notification.StatusNotification.DisplayMode.FLOAT_MODE;
import static org.eclipse.che.ide.api.notification.StatusNotification.Status.*;
import static org.eclipse.che.ide.workspace.perspectives.project.ProjectPerspective.PROJECT_PERSPECTIVE_ID;
//import org.eclipse.che.ide.ext.java.testing.junit4x.client.view.TestRunnerPresenter;
//import org.eclipse.che.ide.ext.java.client.projecttree.JavaSourceFolderUtil;
//import org.eclipse.che.ide.ext.java.client.action.JavaEditorAction;

public class RunClassContextTestAction extends AbstractPerspectiveAction {

    private final NotificationManager notificationManager;
    private final EditorAgent editorAgent;
    private final TestResultPresenter presenter;
    private final TestServiceClient service;
    private final DtoUnmarshallerFactory dtoUnmarshallerFactory;
    private final AppContext appContext;
    private final SelectionAgent selectionAgent;

    @Inject
    public RunClassContextTestAction(JUnitTestResources resources, NotificationManager notificationManager, EditorAgent editorAgent,
                                     AppContext appContext, TestResultPresenter presenter,
                                     TestServiceClient service, DtoUnmarshallerFactory dtoUnmarshallerFactory,
                                     SelectionAgent selectionAgent, JUnitTestLocalizationConstant localization) {
        super(Arrays.asList(PROJECT_PERSPECTIVE_ID), localization.actionRunClassContextTitle(),
                localization.actionRunClassContextDescription(), null, resources.testIcon());
        this.notificationManager = notificationManager;
        this.editorAgent = editorAgent;
        this.presenter = presenter;
        this.service = service;
        this.dtoUnmarshallerFactory = dtoUnmarshallerFactory;
        this.appContext = appContext;
        this.selectionAgent = selectionAgent;
    }

    @Override
    public void actionPerformed(ActionEvent e) {
        final StatusNotification notification = new StatusNotification("Running Tests...", PROGRESS, FLOAT_MODE);
        notificationManager.notify(notification);

        final Selection<?> selection = selectionAgent.getSelection();
        final Object possibleNode = selection.getHeadElement();
        Log.info(TestResultPresenter.class, possibleNode.toString());
        if (possibleNode instanceof FileNode) {
            VirtualFile file = ((FileNode) possibleNode).getData();


            final Project project = appContext.getRootProject();
//        EditorPartPresenter editorPart = editorAgent.getActiveEditor();
//        final VirtualFile file = editorPart.getEditorInput().getFile();
            String fqn = JavaUtil.resolveFQN(file);
            Unmarshallable<TestResult> unmarshaller = dtoUnmarshallerFactory.newWSUnmarshaller(TestResult.class);

            Map<String,String> parameters = new HashMap<>();
            parameters.put("fqn",fqn);
            parameters.put("runClass","true");
            parameters.put("updateClasspath","true");

            service.run(project.getPath(), "junit", parameters,
                    new RequestCallback<TestResult>(unmarshaller) {
                        @Override
                        protected void onSuccess(TestResult result) {
                            Log.info(TestResultPresenter.class, result);
                            notification.setStatus(SUCCESS);
                            if (result.isSuccess()) {
                                notification.setTitle("Test runner executed successfully");
                                notification.setContent("All tests are passed");
                            } else {
                                notification.setTitle("Test runner executed successfully with test failures.");
                                notification.setContent(result.getFailureCount() + " tests are failed.\n");
                            }
                            presenter.handleResponse(result);
                        }

                        @Override
                        protected void onFailure(Throwable exception) {
                            final String errorMessage = (exception.getMessage() != null)
                                    ? exception.getMessage()
                                    : "Failed to run test cases";
                            notification.setContent(errorMessage);
                            notification.setStatus(FAIL);
                        }
                    }
            );
        }
//        presenter.showDialog();
    }

    @Override
    public void updateInPerspective(@NotNull ActionEvent e) {
        if ((appContext.getRootProject() == null)) {
            e.getPresentation().setVisible(true);
            e.getPresentation().setEnabled(false);
            return;
        }

        final Selection<?> selection = selectionAgent.getSelection();

        if (selection == null || selection.isEmpty()) {
            e.getPresentation().setEnabled(false);
            return;
        }

        if (selection.isMultiSelection()) {
            //this is temporary commented
            e.getPresentation().setEnabled(false);
            return;
        }

        final Object possibleNode = selection.getHeadElement();

        boolean enable = possibleNode instanceof FileNode
                && ((FileNode)possibleNode).getData().getExtension().equals("java");

        e.getPresentation().setEnabled(enable);
    }
}
