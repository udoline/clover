/**
 * Provides classes to optimize a set of Tests programatically using Clover's Test Optimzation.
 * <p/>
 * A {@link com.atlassian.clover.api.optimization.TestOptimizer} takes an instance of
 * {@link com.atlassian.clover.api.optimization.OptimizationOptions}.
 * <p/>
 * {@link com.atlassian.clover.api.optimization.OptimizationOptions} are built and configured using an
 * {@link com.atlassian.clover.api.optimization.OptimizationOptions.Builder}.
 *
 * <h2> Example </h2>
 * <pre>
 * final Options options = new Options.Builder().snapshot(new File(".clover/clover.snapshot")).build();
 * final Collection&lt;Class&gt; optimizedClasses = new TestOptimizer(options).optimize(unoptimizedClasses);
 * // optimizedClasses will only contain those tests that cover code that has been modified.
 * </pre>
 *
 *
 * @since 2.6.0
 */
package com.atlassian.clover.api.optimization;