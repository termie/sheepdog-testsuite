/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package com.nebula.sheeptester.controller;

import com.nebula.sheeptester.controller.config.RootConfiguration;
import com.nebula.sheeptester.controller.config.TestConfiguration;
import com.nebula.sheeptester.target.TargetMain;
import java.io.File;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import javax.annotation.Nonnull;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.GnuParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.OptionBuilder;
import org.apache.commons.cli.Options;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.oro.text.GlobCompiler;
import org.apache.oro.text.regex.Pattern;
import org.apache.oro.text.regex.PatternMatcher;
import org.apache.oro.text.regex.Perl5Matcher;
import org.simpleframework.xml.Serializer;
import org.simpleframework.xml.core.PersistenceException;
import org.simpleframework.xml.core.Persister;

/**
 *
 * @author shevek
 */
public class ControllerMain {

    private static final Log LOG = LogFactory.getLog(ControllerMain.class);
    public static final String OPT_HELP = "help";
    public static final String OPT_CONFIG = "config";
    public static final String OPT_JAR = "jar";
    public static final String OPT_COLLIE = "collie";
    public static final String OPT_SHEEP = "sheep";
    public static final String OPT_TEST = "test";
    public static final String OPT_TARGET = "target";
    public static final String OPT_VERBOSE = "verbose";
    public static final int DFLT_THREADS = 100;

    /**
     * @param args the command line arguments
     */
    public static void main(String[] args) throws Exception {
        Options options = new Options();
        options.addOption(OptionBuilder.withDescription("Display help.").create(OPT_HELP));
        options.addOption(OptionBuilder.hasArg().withDescription("Name of a configuration file.").create(OPT_CONFIG));
        options.addOption(OptionBuilder.hasArg().withDescription("Tests to execute.").create(OPT_TEST));
        options.addOption(OptionBuilder.hasArg().withDescription("Path to sheeptester JAR.").create(OPT_JAR));
        options.addOption(OptionBuilder.hasArg().withDescription("Path to sheep binary.").create(OPT_SHEEP));
        options.addOption(OptionBuilder.hasArg().withDescription("Path to collie binary.").create(OPT_COLLIE));
        options.addOption(OptionBuilder.withDescription("Operate as if on the target host (Do not use).").create(OPT_TARGET));
        options.addOption(OptionBuilder.withDescription("Operate verbosely.").create(OPT_VERBOSE));

        CommandLineParser cmdparser = new GnuParser();
        CommandLine cmdline = cmdparser.parse(options, args);

        if (cmdline.hasOption(OPT_TARGET)) {
            TargetMain.main(ArrayUtils.EMPTY_STRING_ARRAY);
            return;
        }

        if (cmdline.hasOption(OPT_HELP) || !cmdline.hasOption(OPT_CONFIG)) {
            HelpFormatter formatter = new HelpFormatter();
            formatter.setLongOptPrefix("--");
            formatter.printHelp("sheeptester-app.jar --" + OPT_CONFIG + "=<config-file>", options);
            return;
        }

        RootConfiguration configuration = null;
        {
            String[] pathlists = cmdline.getOptionValues(OPT_CONFIG);
            for (String pathlist : pathlists) {
                for (String path : StringUtils.split(pathlist, ", ")) {
                    InputStream in = FileUtils.openInputStream(new File(path));
                    try {
                        Serializer serializer = new Persister();
                        RootConfiguration config = serializer.read(RootConfiguration.class, in);
                        if (configuration == null)
                            configuration = config;
                        else
                            configuration.addConfiguration(config);
                    } catch (PersistenceException e) {
                        LOG.error("Failed to load configuration file: " + e.getMessage());
                        return;
                    } finally {
                        IOUtils.closeQuietly(in);
                    }
                }
            }
        }

        ControllerContext context = new ControllerContext(configuration, cmdline);

        try {
            context.init(); // Make the finally clean up after a partial init()

            for (TestConfiguration test : configuration.getTests())
                test.check(context);

            List<TestConfiguration> tests = new ArrayList<TestConfiguration>();
            GlobCompiler compiler = new GlobCompiler();
            PatternMatcher matcher = new Perl5Matcher();
            for (String testGlob : getTests(cmdline)) {
                Pattern pattern = compiler.compile(testGlob);
                boolean found = false;
                for (TestConfiguration test : configuration.getTests()) {
                    if (isTest(test, matcher, pattern, testGlob)) {
                        found = true;
                        tests.add(test);
                    }
                }
                if (!found)
                    throw new NullPointerException("No such test " + testGlob);
            }

            LOG.info("Running tests:");
            for (TestConfiguration test : tests) {
                LOG.info(test.getId());
            }

            Map<String, String> results = new TreeMap<String, String>();
            for (TestConfiguration test : tests) {
                String testId = test.getId();
                try {
                    LOG.info("\n\n---\n\n");
                    results.put(testId, "Started...");
                    test.run(context);
                    results.put(testId, "OK");
                } catch (ControllerAssertionException e) {
                    LOG.error("Test failed: " + e.getMessage());
                    results.put(testId, e.getMessage());
                }
            }

            LOG.info("");
            LOG.info("");

            if (context.isVerbose()) {
                LOG.info("=== Skipped Tests ===");
                List<TestConfiguration> skipped = new ArrayList<TestConfiguration>();
                for (TestConfiguration config : configuration.getTests()) {
                    if (!results.containsKey(config.getId())) {
                        skipped.add(config);
                    }
                }
                Collections.sort(skipped, new Comparator<TestConfiguration>() {

                    @Override
                    public int compare(TestConfiguration o1, TestConfiguration o2) {
                        return o1.getId().compareTo(o2.getId());
                    }
                });
                for (TestConfiguration config : skipped) {
                    LOG.info(config.getId() + ": " + config.getDescription());
                }
            }

            LOG.info("=== Test Results ===");
            for (Map.Entry<String, String> e : results.entrySet()) {
                String value = e.getValue();
                int idx = value.indexOf('\n');
                if (idx > 0)
                    value = value.substring(0, idx);
                LOG.info(e.getKey() + ": " + value);
            }

        } catch (ControllerException e) {
            LOG.error("Failed.", e);
        } catch (InterruptedException e) {
            LOG.error("Interrupted!", e);
        } finally {
            context.fini();
        }
    }

    @Nonnull
    private static List<String> getTests(CommandLine cmdline) {
        String[] tests = cmdline.getOptionValues(OPT_TEST);
        if (tests == null)
            return Collections.singletonList("*");
        List<String> out = new ArrayList<String>();
        for (String test : tests)
            out.addAll(Arrays.asList(StringUtils.split(test, ", ")));
        return out;
    }

    private static boolean isTest(@Nonnull TestConfiguration config, @Nonnull PatternMatcher matcher, @Nonnull Pattern pattern, String arg) {
        // Exact matches always match.
        if (arg.equals(config.getId()))
            return true;
        if (config.getGroups().contains(arg))
            return true;
        // Nonexact matches or glob matches require 'auto'.
        if (!config.isAuto())
            return false;
        if (matcher.matches(config.getId(), pattern))
            return true;
        for (String groupId : config.getGroups())
            if (matcher.matches(groupId, pattern))
                return true;
        return false;
    }
}