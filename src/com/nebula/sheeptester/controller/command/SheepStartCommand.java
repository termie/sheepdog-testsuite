/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package com.nebula.sheeptester.controller.command;

import com.nebula.sheeptester.controller.ControllerContext;
import com.nebula.sheeptester.controller.config.SheepConfiguration;
import com.nebula.sheeptester.controller.model.Host;
import com.nebula.sheeptester.controller.model.Sheep;
import com.nebula.sheeptester.target.operator.SheepStartOperator;
import java.util.concurrent.ExecutionException;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.simpleframework.xml.Attribute;
import org.simpleframework.xml.Root;

/**
 *
 * @author shevek
 */
@Root(name = "sheep-start")
public class SheepStartCommand extends AbstractCommand {

    private static final Log LOG = LogFactory.getLog(SheepStartCommand.class);
    @Attribute(required = false)
    private String sheepId;

    @Override
    public void run(ControllerContext context) throws InterruptedException, ExecutionException {
        Sheep sheep = toSheep(context, sheepId);
        if (sheep.isRunning()) {
            LOG.warn("Sheep already running: " + sheep);
            return;
        }
        SheepConfiguration config = sheep.getConfig();
        SheepStartOperator operator = new SheepStartOperator(config.getPort(), config.getDirectory());

        Host host = sheep.getHost();
        context.execute(host, operator);
    }

    @Override
    public void toStringBuilderArgs(StringBuilder buf) {
        super.toStringBuilderArgs(buf);
        if (sheepId != null)
            buf.append(" sheepId=").append(sheepId);
        else
            buf.append(" <all-sheep>");
    }
}
