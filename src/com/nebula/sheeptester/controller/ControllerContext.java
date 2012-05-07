/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package com.nebula.sheeptester.controller;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.jcraft.jsch.JSch;
import com.jcraft.jsch.JSchException;
import com.jcraft.jsch.KnownHosts;
import com.nebula.sheeptester.controller.command.SheepStatCommand;
import com.nebula.sheeptester.controller.config.HostConfiguration;
import com.nebula.sheeptester.controller.config.RootConfiguration;
import com.nebula.sheeptester.controller.config.SheepConfiguration;
import com.nebula.sheeptester.controller.model.Host;
import com.nebula.sheeptester.controller.model.Sheep;
import com.nebula.sheeptester.controller.model.Vdi;
import com.nebula.sheeptester.target.operator.ConfigOperator;
import com.nebula.sheeptester.target.operator.SheepListOperator;
import com.nebula.sheeptester.target.operator.Operator;
import com.nebula.sheeptester.target.operator.OperatorAdapter;
import com.nebula.sheeptester.target.operator.OperatorResponseAdapter;
import com.nebula.sheeptester.target.operator.Response;
import java.io.File;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URL;
import java.net.URLDecoder;
import java.security.CodeSource;
import java.security.ProtectionDomain;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import javax.annotation.CheckForNull;
import javax.annotation.Nonnull;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.lang3.SystemUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

/**
 *
 * @author shevek
 */
public class ControllerContext {

    private static final Log LOG = LogFactory.getLog(ControllerContext.class);
    private final RootConfiguration configuration;
    private final JSch jsch = new JSch();
    private final int nthreads;
    private final ExecutorService executor;
    private final Gson gson;
    private final File jarFile;
    private final File sheepFile;
    private final File collieFile;
    private ConcurrentMap<String, Host> hostMap = new ConcurrentHashMap<String, Host>();
    private ConcurrentMap<String, Sheep> sheepMap = new ConcurrentHashMap<String, Sheep>();
    private ConcurrentMap<String, Vdi> vdiMap = new ConcurrentHashMap<String, Vdi>();

    public ControllerContext(RootConfiguration configuration, CommandLine cmdline) throws UnsupportedEncodingException, JSchException {
        this.configuration = configuration;
        File hosts = new File(SystemUtils.getUserHome(), ".ssh/known_hosts");
        this.jsch.setKnownHosts(hosts.getAbsolutePath());

        this.nthreads = getOptionInteger(cmdline, ControllerMain.OPT_THREADS, ControllerMain.DFLT_THREADS);
        this.executor = Executors.newFixedThreadPool(nthreads);
        GsonBuilder builder = new GsonBuilder();
        builder.registerTypeAdapter(Operator.class, new OperatorAdapter());
        builder.registerTypeAdapter(Response.class, new OperatorResponseAdapter());
        this.gson = builder.create();

        if (cmdline.hasOption(ControllerMain.OPT_JAR)) {
            this.jarFile = new File(cmdline.getOptionValue(ControllerMain.OPT_JAR));
        } else {
            ProtectionDomain pd = getClass().getProtectionDomain();
            CodeSource cs = pd.getCodeSource();
            URL location = cs.getLocation();
            String path = URLDecoder.decode(location.getPath(), "UTF-8");
            if (path.startsWith("jar:"))
                path = path.substring(4);
            if (path.startsWith("file:"))
                path = path.substring(5);
            if (path.lastIndexOf('!') != -1)
                path = path.substring(0, path.lastIndexOf('!'));
            this.jarFile = new File(path);
        }

        if (!jarFile.exists())
            throw new IllegalStateException("Self-jar " + jarFile + " does not exist.");
        this.sheepFile = new File(cmdline.getOptionValue(ControllerMain.OPT_SHEEP, "sheep"));
        this.collieFile = new File(cmdline.getOptionValue(ControllerMain.OPT_COLLIE, "collie"));
    }

    @Nonnull
    public ExecutorService getExecutor() {
        return executor;
    }

    @Nonnull
    public Gson getGson() {
        return gson;
    }

    @Nonnull
    public JSch getJsch() {
        return jsch;
    }

    @Nonnull
    public File getJarFile() {
        return jarFile;
    }

    @Nonnull
    public File getSheepFile() {
        return sheepFile;
    }

    @Nonnull
    public File getCollieFile() {
        return collieFile;
    }

    @Nonnull
    public Collection<? extends Host> getHosts() {
        return new ArrayList<Host>(hostMap.values());
    }

    @CheckForNull
    public Host getHost(String id) {
        return hostMap.get(id);
    }

    @Nonnull
    public Map<? extends String, ? extends Sheep> getSheep() {
        return sheepMap;
    }

    @Nonnull
    public Map<? extends String, ? extends Sheep> getSheep(Host host) {
        Map<String, Sheep> out = new HashMap<String, Sheep>();
        for (Map.Entry<? extends String, ? extends Sheep> e : sheepMap.entrySet()) {
            if (e.getValue().getHost() == host)
                out.put(e.getKey(), e.getValue());
        }
        return out;
    }

    @CheckForNull
    public Sheep getSheep(String id) {
        return sheepMap.get(id);
    }

    @Nonnull
    public Map<? extends String, ? extends Vdi> getVdis() {
        return vdiMap;
    }

    @CheckForNull
    public Vdi getVdi(String id) {
        return vdiMap.get(id);
    }

    public void addError(@Nonnull String message, @CheckForNull Throwable t) {
        LOG.error(message, t);
    }

    private static int getOptionInteger(@Nonnull CommandLine cmdline, @Nonnull String option, int dflt) {
        String value = cmdline.getOptionValue(option);
        if (value == null)
            return dflt;
        return Integer.parseInt(value);
    }

    public void init() throws IOException, InterruptedException {
        for (HostConfiguration config : configuration.getHosts()) {
            config.init();
            Host host = new Host(this, config);
            hostMap.put(config.getId(), host);
        }

        for (SheepConfiguration config : configuration.getSheeps()) {
            config.init();
            Host host = getHost(config.getHostId());
            if (host == null)
                throw new IllegalStateException("Cannot find host for sheep " + config);
            Sheep sheep = new Sheep(host, config);
            sheepMap.put(config.getId(), sheep);
        }

        final CountDownLatch latch = new CountDownLatch(hostMap.size());
        for (final Map.Entry<String, Host> e : hostMap.entrySet()) {
            getExecutor().submit(new Runnable() {

                @Override
                public void run() {
                    String hostId = e.getKey();
                    Host host = e.getValue();
                    try {
                        HostConfiguration config = host.getConfig();
                        host.connect();
                        execute(host, new ConfigOperator(hostId, config.getSheep(), config.getCollie()));
                        SheepStatCommand.run(ControllerContext.this, host);
                    } catch (Exception e) {
                        addError("Failed while executing on " + host, e);
                    } finally {
                        latch.countDown();
                    }
                }
            });
        }
        latch.await();
    }

    public void fini() throws InterruptedException, ExecutionException {
        for (Host host : hostMap.values()) {
            host.disconnect();
        }
        executor.shutdown();
    }

    public Response execute(@Nonnull Host host, @Nonnull Operator operator) throws InterruptedException, ExecutionException {
        return host.execute(this, operator);
    }
}