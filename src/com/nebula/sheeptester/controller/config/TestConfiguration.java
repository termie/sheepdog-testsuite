/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package com.nebula.sheeptester.controller.config;

import com.nebula.sheeptester.controller.ControllerContext;
import com.nebula.sheeptester.controller.ControllerException;
import com.nebula.sheeptester.controller.command.AbstractMultiCommand;
import com.nebula.sheeptester.controller.command.Command;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import javax.annotation.CheckForNull;
import javax.annotation.Nonnull;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.simpleframework.xml.Attribute;
import org.simpleframework.xml.Root;

/**
 *
 * @author shevek
 */
@Root(name = "test")
public class TestConfiguration extends AbstractMultiCommand {

    private static final Log LOG = LogFactory.getLog(TestConfiguration.class);
    private static final AtomicInteger COUNTER = new AtomicInteger();
    @Attribute(required = false)
    private String id;
    @Attribute(required = false)
    private String description;
    @Attribute(required = false)
    private String groups;
    @Attribute(required = false)
    private boolean auto = true;

    @Nonnull
    public String getId() {
        if (id == null)
            id = "_test_" + COUNTER.getAndIncrement();
        return id;
    }

    @CheckForNull
    public String getDescription() {
        return description;
    }

    @Nonnull
    public List<? extends String> getGroups() {
        if (groups == null)
            return Collections.emptyList();
        return Arrays.asList(StringUtils.split(groups, ", "));
    }

    public boolean isAuto() {
        return auto;
    }

    @Override
    public void run(@Nonnull ControllerContext context) throws ControllerException, InterruptedException {
        try {
            LOG.info("=== Starting test " + getId() + " ===");
            context.clearProperties();
            for (Command command : getCommands())
                command.run(context);
        } finally {
            LOG.info("=== Finished test " + getId() + " ===");
        }
    }

    @Override
    public String toString() {
        StringBuilder buf = new StringBuilder();
        buf.append(getId()).append(":\n");
        toStringBuilder(buf, 0);
        int length = buf.length();
        return buf.substring(0, length - 1);
    }
}