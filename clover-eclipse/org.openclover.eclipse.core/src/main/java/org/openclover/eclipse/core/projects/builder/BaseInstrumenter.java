package org.openclover.eclipse.core.projects.builder;

import com.atlassian.clover.api.instrumentation.ConcurrentInstrumentationException;
import com.atlassian.clover.api.instrumentation.InstrumentationSession;
import com.atlassian.clover.cfg.instr.java.JavaInstrumentationConfig;
import com.atlassian.clover.instr.java.Instrumenter;
import com.atlassian.clover.api.CloverException;
import org.openclover.eclipse.core.CloverPlugin;
import org.openclover.eclipse.core.exclusion.ExclusionFilter;
import org.openclover.eclipse.core.projects.CloverProject;
import com.atlassian.clover.registry.Clover2Registry;
import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.IncrementalProjectBuilder;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.swt.widgets.Display;

import java.io.IOException;
import java.util.Arrays;
import java.util.Iterator;

public abstract class BaseInstrumenter {
    protected static final String INSTRUMENTATION_PROBLEM_MARKER = CloverPlugin.ID + ".markers.instrumentation.problem";
    protected static final int LOAD_STORE_PROGRESS = 25;

    protected final Instrumenter instrumenter;
    protected final InstrumentationProjectPathMap instrumentationMapper;
    protected final ExclusionFilter exclusionFilter;
    protected final boolean isDebugging;
    protected final CloverProject project;
    protected final IProgressMonitor monitor;
    protected final boolean fullBuild;
    protected final Clover2Registry registry;
    protected boolean hasInstrumented;
    protected JavaInstrumentationConfig config;

    public BaseInstrumenter(IProgressMonitor monitor, InstrumentationProjectPathMap pathMap, CloverProject project, Clover2Registry registry, final int buildKind) throws CoreException {
        this.monitor = monitor;
        this.instrumentationMapper = pathMap;
        this.project = project;
        this.registry = registry;
        this.config = project.newInsturmentationConfig();
        this.fullBuild = buildKind == IncrementalProjectBuilder.CLEAN_BUILD || buildKind == IncrementalProjectBuilder.FULL_BUILD;
        this.instrumenter = new Instrumenter(CloverPlugin.getInstance().getCloverLogger(), config) {
            @Override
            protected void finishAndApply(final InstrumentationSession session) throws ConcurrentInstrumentationException {
                //Do model updates (but not saving) in UI thread to avoid MT problems but only if an incremental build
                final ConcurrentInstrumentationException[] uiThreadException =
                    new ConcurrentInstrumentationException[] { null };
                final Runnable finishWork = () -> {
                    try {
                        session.close();
                    } catch (ConcurrentInstrumentationException e) {
                        uiThreadException[0] = e;
                    }
                };
                if (fullBuild) {
                    finishWork.run();
                } else {
                    Display.getDefault().syncExec(finishWork);
                }
                if (uiThreadException[0] != null) {
                    throw uiThreadException[0];
                }
            }
        };
        this.exclusionFilter = new ExclusionFilter(project.getSettings());
        this.isDebugging = CloverPlugin.getInstance().isDebugging();
    }

    public void start(int expectedFileCount) {
        monitor.beginTask("Instrumenting project source", 2 * LOAD_STORE_PROGRESS + expectedFileCount);
    }

    public void finish(boolean successful) throws InstrumentationException {
        monitor.subTask("Finalising source instrumentation");
        if (hasInstrumented) {
            try {
                long start = System.currentTimeMillis();
                CloverPlugin.logVerbose("Storing model after instrumentation");
                instrumenter.endInstrumentation(true);
                CloverPlugin.logVerbose("Storing model ended (" + (System.currentTimeMillis() - start) + " ms)");
                monitor.worked(LOAD_STORE_PROGRESS);

                if (!isIncremental()) {
                    project.flagStaleRegistryBecause(null);
                }
            } catch (CloverException e) {
                throw new InstrumentationException(e);
            }
        }
    }


    public void instrumentOrCopySource(IFile file) throws CoreException {
        try {
            if (filteredOut(file)) {
                copySource(file);
            } else {
                instrumentSource(file);
            }
        } catch (CloverException e) {
            throw new InstrumentationException(e);
        }
    }

    protected abstract void copySource(IFile originalFile) throws CloverException, CoreException;

    protected abstract void instrumentSource(IFile originalFile) throws CloverException, CoreException;

    private boolean filteredOut(IFile file) {

        boolean filteredOut = exclusionFilter.isFilteredOut(file);

        if (filteredOut && isDebugging) {
            CloverPlugin.logError("Source file " + file.getProjectRelativePath().toPortableString()
                    + " skipped owing to Clover workingset or project instrumentation filters: [include = "
                    + Arrays.toString(exclusionFilter.getIncludeFilter())
                    + "] [exclude = "
                    + Arrays.toString(exclusionFilter.getExcludeFilter())
                    + "]");
        }
        return filteredOut;
    }

    protected void removeMarkers(IFile originalFile) throws CoreException {
        originalFile.deleteMarkers(INSTRUMENTATION_PROBLEM_MARKER, true, IResource.DEPTH_ONE);
    }

    protected void maybeInitialiseInstrumentation() throws InstrumentationInitialisationException {
        if (!hasInstrumented) {
            try {
                registry.setContextStore(project.getSettings().getContextRegistry());
                startInstrumentation();
            } catch (CloverException e) {
                throw new InstrumentationInitialisationException(e);
            } finally {
                monitor.worked(LOAD_STORE_PROGRESS);
            }
        }
    }

    private void startInstrumentation() throws CloverException {
        long start = System.currentTimeMillis();
        CloverPlugin.logVerbose("Starting instrumentation");
        instrumenter.startInstrumentation(registry);
        CloverPlugin.logVerbose("Instrumentation started (" + (System.currentTimeMillis() - start) + " ms)");
    }

    private boolean isIncremental() {
        return !fullBuild;
    }

    private Clover2Registry truncateRegistry() throws CloverException {
        Clover2Registry reg;
        try {
            reg = Clover2Registry.createOrLoad(config.getRegistryFile(), config.getProjectName());
        } catch (IOException e1) {
            try {
                Markers.deleteCloverStaleDbMarkers(project.getProject());
                Markers.createCloverStaleDbMarker(
                    project.getProject(),
                    "Clover could not create a fresh instrumentation database:\n\n" + e1.getMessage());
            } catch (CoreException e2) {
                CloverPlugin.logError("Unable to create problem marker for database ", e2);
            }
            throw new CloverException("Clover could not create a fresh instrumentation database", e1);
        }
        return reg;
    }

    protected void addInstrumentationFailure(IFile originalFile, Exception e) throws CoreException {
        project.addInstrumentationFailure(originalFile);
        CloverPlugin.logError("Clover instrumentation failed for file " + originalFile + " - code coverage will not be recorded for this file.", e);
    }

    public abstract Iterator fileNamesAsCompilerArg();

    public char[] getContentsFor(String filename) {
        throw new UnsupportedOperationException();
    }

    public abstract boolean willRenderContentsFor(String filename);
}
