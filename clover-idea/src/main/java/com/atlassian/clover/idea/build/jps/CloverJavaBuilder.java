package com.atlassian.clover.idea.build.jps;

import com.atlassian.clover.api.CloverException;
import com.atlassian.clover.cfg.instr.java.JavaInstrumentationConfig;
import com.atlassian.clover.cfg.instr.java.SourceLevel;
import com.atlassian.clover.instr.java.Instrumenter;
import com.atlassian.clover.spi.lang.Language;
import com.atlassian.clover.context.ContextStore;
import com.atlassian.clover.context.MethodRegexpContext;
import com.atlassian.clover.context.RegexpContext;
import com.atlassian.clover.context.StatementRegexpContext;
import com.atlassian.clover.idea.config.CloverPluginConfig;
import com.atlassian.clover.idea.config.regexp.Regexp;
import com.atlassian.clover.util.trie.FilePathPrefixTree;
import com.atlassian.clover.registry.Clover2Registry;
import com.intellij.openapi.diagnostic.Logger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.ModuleChunk;
import org.jetbrains.jps.builders.DirtyFilesHolder;
import org.jetbrains.jps.builders.java.JavaSourceRootDescriptor;
import org.jetbrains.jps.incremental.BuilderCategory;
import org.jetbrains.jps.incremental.CompileContext;
import org.jetbrains.jps.incremental.ModuleBuildTarget;
import org.jetbrains.jps.incremental.ModuleLevelBuilder;
import org.jetbrains.jps.incremental.ProjectBuildException;
import org.jetbrains.jps.incremental.messages.BuildMessage;
import org.jetbrains.jps.incremental.messages.CompilerMessage;
import org.jetbrains.jps.model.JpsProject;
import org.jetbrains.jps.model.java.LanguageLevel;
import org.jetbrains.jps.model.module.JpsModule;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Clover builder for a module -  handles start/end of build, shares an instance of Clover's Instrumenter class between
 * {@link CloverJavaSourceTransformer#transform(File, CharSequence)} calls.
 */
public class CloverJavaBuilder extends ModuleLevelBuilder {

    private static final CloverJavaBuilder INSTANCE;

    static {
        INSTANCE = new CloverJavaBuilder();
    }

    private final Logger LOG = Logger.getInstance(CloverJavaBuilder.class.getName());

    /**
     * Current compilation context.
     */
    private volatile CompileContext compileContext;

    /**
     * Cache. Mapping of source roots to java language level.
     */
    private volatile FilePathPrefixTree<LanguageLevel> sourceRootToLanguageLevel;

    /**
     * We want to manage the instance of the CloverInstrumenter, using it for one 'complete' Compile Cycle. Why? Every
     * time we create a new instance, we trigger the downstream creation of a new coverage data file.
     */
    private Instrumenter instr;


    public static CloverJavaBuilder getInstance() {
        return INSTANCE;
    }


    private CloverJavaBuilder() {
        super(BuilderCategory.SOURCE_INSTRUMENTER);
    }

    /**
     * {@inheritDoc}
     */
    @NotNull
    @Override
    public String getPresentableName() {
        return "clover-java";
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void buildStarted(final CompileContext context) {
        super.buildStarted(context);
        synchronized (this) {
            sourceRootToLanguageLevel = JpsProjectPrefixTreeUtil.collectLanguageLevels(context.getProjectDescriptor().getProject());
            compileContext = context;
            instr = createInstrumenter();
        }

        LOG.info("CloverJavaBuilder.buildStarted called. context=" + context);
        sendCompilerMessageToIDE(BuildMessage.Kind.INFO, "CloverJavaBuilder external build starts");
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void buildFinished(CompileContext context) {
        super.buildFinished(context);
        LOG.info("CloverJavaBuilder.buildFinished called. context=" + context);
        sendCompilerMessageToIDE(BuildMessage.Kind.PROGRESS, "CloverJavaBuilder build is finished");
        synchronized (this) {
            closeInstrumentationSession();
            compileContext = null;
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public ExitCode build(CompileContext context, ModuleChunk chunk, DirtyFilesHolder<JavaSourceRootDescriptor, ModuleBuildTarget> dirtyFilesHolder, OutputConsumer outputConsumer) throws ProjectBuildException, IOException {
        LOG.info("CloverJavaBuilder.build called. context=" + context
                + " chunk=" + chunk + " dirtyFilesHolder=" + dirtyFilesHolder
                + " outputConsumer=" + outputConsumer);

        String msg = "CloverJavaBuilder processing module chunk " + chunk.getName() + " with modules: ";
        for (JpsModule module : chunk.getModules()) {
            msg += module.getName() + ",";
        }
        sendCompilerMessageToIDE(BuildMessage.Kind.PROGRESS, msg);

        return ExitCode.OK;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public List<String> getCompilableFileExtensions() {
        final List<String> extensionWithoutDot = new ArrayList<>(Language.Builtin.JAVA.getFileExtensions().size());
        // remove dots as required by ModuleLevelBuilder.getCompilableFileExtensions() API
        for (final String ext : Language.Builtin.JAVA.getFileExtensions()) {
            extensionWithoutDot.add(ext.substring(1));
        }
        return extensionWithoutDot;
    }

    /**
     * Return current compilation context.
     *
     * @return CompileContext
     */
    public CompileContext getCompileContext() {
        return compileContext;
    }


    public FilePathPrefixTree<LanguageLevel> getSourceRootToLanguageLevel() {
        return sourceRootToLanguageLevel;
    }

    /**
     * Return instance of the Clover's Java instrumenter.
     *
     * @return Instumenter
     */
    public Instrumenter getInstrumenter() {
        return instr;
    }

    /**
     * Logs error message and sends notification back to IDE.
     *
     * @param message text
     */
    protected void sendErrorNotification(final String message) {
        sendCompilerMessageToIDE(BuildMessage.Kind.ERROR, message);
        LOG.error(message);
    }

    /**
     * Sends message notification back to IDE.
     *
     * @param kind    message level (error, warning etc)
     * @param message text
     */
    protected synchronized void sendCompilerMessageToIDE(final BuildMessage.Kind kind, final String message) {
        if (compileContext != null) {
            compileContext.processMessage(new CompilerMessage("", kind, message));
        }
    }

    /**
     * Create InstrumentationConfig object using data from CloverPluginConfig. Attach test detector for JpsProject.
     *
     * @param config  clover plugin configuration
     * @param project current project
     * @return InstrumentationConfig
     */
    private JavaInstrumentationConfig createInstrumentationConfig(final CloverPluginConfig config, final JpsProject project) {
        final JavaInstrumentationConfig instrConfig = new JavaInstrumentationConfig();
        instrConfig.setInitstring(config.getInitString());
        instrConfig.setFlushPolicy(config.getFlushPolicy());
        instrConfig.setFlushInterval(config.getFlushInterval());
        instrConfig.setSourceLevel(SourceLevel.fromString(config.getLanguageLevel()));
        instrConfig.setTestDetector(new JpsProjectTestDetector(project, JpsProjectPrefixTreeUtil.collectRootTypes(project)));
        instrConfig.setInstrumentLambda(config.getInstrumentLambda());
        return instrConfig;
    }

    /**
     * Create Instrumenter for Java.
     *
     * @return Instrumenter
     */
    private Instrumenter createInstrumenter() {
        if (!isCloverInstrumentationEnabled()) {
            return null;
        }

        if (instr == null) {
            // fetch serialized configuration associated with current compilation context
            final CloverPluginConfig pluginConfig = JpsModelUtil.getCloverPluginConfig(compileContext.getProjectDescriptor().getProject());
            if (pluginConfig == null) {
                sendErrorNotification("Clover was unable to find its configuration data associated with the current project");
                return null;
            }
            final JavaInstrumentationConfig instrConfig = createInstrumentationConfig(pluginConfig,
                    compileContext.getProjectDescriptor().getProject());
            final Instrumenter instrumenter = new Instrumenter(instrConfig);

            // create (or load) clover database
            final Clover2Registry registry;
            try {
                registry = Clover2Registry.createOrLoad(new File(instrConfig.getInitString()), instrConfig.getProjectName());
            } catch (IOException | CloverException ex) {
                sendErrorNotification("Clover was unable to instrument your source because of the following error:"
                        + ex + ". Please try to delete the coverage database run build again.");
                return null;
            }

            // store method / statement contexts in the registry
            final List<Regexp> regexpCtxList = pluginConfig.getRegexpContexts();
            if (regexpCtxList != null) {
                try {
                    final ContextStore contextRegistry = new ContextStore();
                    for (final Regexp regexp : regexpCtxList) {
                        if (regexp.isValid()) {
                            final RegexpContext regexpContext = regexp.toContextSetting();
                            if (regexpContext instanceof MethodRegexpContext) {
                                contextRegistry.addMethodContext((MethodRegexpContext) regexpContext);
                            } else {
                                contextRegistry.addStatementContext((StatementRegexpContext) regexpContext);
                            }
                        }
                    }
                    registry.setContextStore(contextRegistry);
                } catch (CloverException ex) {
                    final String msg = "Clover was unable to process method or statement context regular expressions. See IDEA log for more details.";
                    sendCompilerMessageToIDE(BuildMessage.Kind.ERROR, msg);
                    LOG.error(msg, ex);
                    return null;
                }
            }

            // start instrumentation session
            try {
                instrumenter.startInstrumentation(registry); // may throw exception here
            } catch (CloverException ex) {
                final String msg = "Clover was unable to start new instrumentation session. See IDEA log for more details.";
                LOG.error(msg, ex);
                sendCompilerMessageToIDE(BuildMessage.Kind.ERROR, msg);
                return null;
            }

            instr = instrumenter; // assign only when initialized without exception
        }
        return instr;
    }

    /**
     * This method returns true if Clover instrumentation is enabled.
     *
     * @return true if Clover instrumentation is enabled.
     */
    private boolean isCloverInstrumentationEnabled() {
        return JpsModelUtil.isBuildWithCloverEnabled(compileContext.getProjectDescriptor().getProject())
                && JpsModelUtil.isCloverEnabled(compileContext.getProjectDescriptor().getProject());
    }

    private void closeInstrumentationSession() {
        if (!isCloverInstrumentationEnabled()) {
            return;
        }

        if (instr != null) {
            try {
                instr.endInstrumentation(true);
                instr = null;
                LOG.info("completed instrumentation.");
            } catch (CloverException ex) {
                LOG.error(ex);
            }
        }
    }

}
