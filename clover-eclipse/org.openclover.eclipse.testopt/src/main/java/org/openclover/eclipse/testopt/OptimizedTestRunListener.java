package org.openclover.eclipse.testopt;

import com.atlassian.clover.CloverDatabase;
import com.atlassian.clover.api.CloverException;
import com.atlassian.clover.CoverageDataSpec;
import org.openclover.eclipse.core.projects.CloverProject;
import com.atlassian.clover.optimization.Snapshot;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.internal.junit.model.TestRunSession;
import org.eclipse.jdt.junit.TestRunListener;
import org.eclipse.jdt.junit.model.ITestRunSession;

import java.io.IOException;

public class OptimizedTestRunListener extends TestRunListener {

    @Override
    public void sessionFinished(ITestRunSession session) {
        if (!(session instanceof TestRunSession)) {
            TestOptimizationPlugin.logInfo("Expected TestRunSession instance, received " + session.getClass().getName());
            return;
        }
        TestRunSession trs = (TestRunSession) session;
        if (OptimizedLaunchingConstants.OPTIMIZED_MODE.equals(trs.getLaunch().getLaunchMode())) {
            final IJavaProject project = trs.getLaunchedProject();
            if (project == null) {
                TestOptimizationPlugin.logInfo("Cannot retrieve project from TestRunSession " + trs.getTestRunName());
                return;
            }
            final CloverProject cloverProject;
            try {
                cloverProject = CloverProject.getFor(project);
            } catch (CoreException e) {
                TestOptimizationPlugin.logInfo("Cannot retrieve CloverProject for project " + project.getElementName(), e);
                return;
            }

            if (cloverProject == null) {
                TestOptimizationPlugin.logInfo("Cannot retrieve CloverProject for project " + project.getElementName());
                return;
            }
            final String initStr = cloverProject.deriveInitString();
            final CloverDatabase db;
            try {
                db = CloverDatabase.loadWithCoverage(initStr, new CoverageDataSpec());
            } catch (CloverException e) {
                TestOptimizationPlugin.logInfo("Cannot load Clover database at " + initStr, e);
                return;
            }
            Snapshot snapshot = Snapshot.loadFor(initStr);
            if (snapshot != null) {
                snapshot.updateFor(db);
            } else {
                TestOptimizationPlugin.logInfo("Generating new snapshot for " + initStr);
                snapshot = Snapshot.generateFor(db);
            }
            try {
                snapshot.store();
            } catch (IOException e) {
                TestOptimizationPlugin.logWarning("Error storing generated snapshot", e);
            }
        }
    }
}
