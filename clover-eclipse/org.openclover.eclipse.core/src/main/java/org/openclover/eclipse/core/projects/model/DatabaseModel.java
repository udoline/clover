package org.openclover.eclipse.core.projects.model;

import com.atlassian.clover.CloverDatabase;
import com.atlassian.clover.registry.entities.FullProjectInfo;
import org.openclover.eclipse.core.projects.CloverProject;
import com.atlassian.clover.registry.entities.BaseClassInfo;
import com.atlassian.clover.registry.entities.BaseFileInfo;
import com.atlassian.clover.api.registry.HasMetrics;
import com.atlassian.clover.registry.entities.FullMethodInfo;
import com.atlassian.clover.registry.entities.TestCaseInfo;
import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IMethod;
import org.eclipse.jdt.core.IPackageFragment;
import org.eclipse.jdt.core.IType;
import org.eclipse.core.runtime.IProgressMonitor;

public abstract class DatabaseModel {
    protected final CloverProject project;
    protected final CoverageModelChangeEvent changeEvent;

    protected DatabaseModel(CloverProject project, CoverageModelChangeEvent changeEvent) {
        this.project = project;
        this.changeEvent = changeEvent;
    }

    public abstract CloverDatabase getDatabase();

    public String toString() {
        return shortClassName() + "[" + project.getName() + "]";
    }

    protected String shortClassName() {
        String name = getClass().getName();
        return name.substring(name.lastIndexOf('.') + 1);
    }

    public abstract boolean isLoaded();
    public abstract boolean isLoading();

    public abstract CloverDatabase forcePrematureLoad(IProgressMonitor monitor);

    public abstract void loadDbAndCoverage(CoverageModelChangeEvent changeEvent);
    public abstract void refreshCoverage(CoverageModelChangeEvent changeEvent);
    public abstract void close(CoverageModelChangeEvent changeEvent);

    public void onActivation(DatabaseModel predecessor) { /*no-op*/ }
    public void onDeactication(DatabaseModel successor) { /*no-op*/ }

    public abstract FullProjectInfo getFullProjectInfo();
    public abstract FullProjectInfo getTestOnlyProjectInfo();
    public abstract FullProjectInfo getAppOnlyProjectInfo();

    public abstract HasMetrics getPackageInfoOrFragment(IPackageFragment pack, MetricsScope scope);
    public abstract BaseFileInfo getSourceFileInfo(ICompilationUnit cu, MetricsScope scope);
    public abstract BaseClassInfo getTypeInfo(IType type, MetricsScope scope);
    public abstract FullMethodInfo getMethodInfo(IMethod method, MetricsScope scope);
    public abstract TestCaseInfo getTestCaseInfo(IMethod method, MetricsScope scope);
    public abstract TestCaseInfo[] getTestCaseInfos(IMethod method, MetricsScope scope);
    public abstract HasMetrics metricsProviderFor(Object projectArtifact, MetricsScope scope);

    public abstract boolean isRegistryOfDate();
    public abstract boolean isCoverageOutOfDate();

    public CoverageModelChangeEvent getLoadEvent() {
        return changeEvent;
    }

    public CloverProject getProject() {
        return project;
    }

}
