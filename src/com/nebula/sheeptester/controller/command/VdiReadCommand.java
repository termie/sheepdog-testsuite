/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package com.nebula.sheeptester.controller.command;

import com.nebula.sheeptester.controller.ControllerContext;
import com.nebula.sheeptester.controller.ControllerException;
import com.nebula.sheeptester.controller.model.Host;
import com.nebula.sheeptester.controller.model.Sheep;
import com.nebula.sheeptester.controller.model.Vdi;
import com.nebula.sheeptester.target.operator.VdiReadOperator;
import com.nebula.sheeptester.util.EscapeUtils;
import javax.annotation.CheckForNull;
import org.simpleframework.xml.Attribute;
import org.simpleframework.xml.Root;

/**
 *
 * @author shevek
 */
@Root(name = "vdi-read")
public class VdiReadCommand extends AbstractCommand {

    @CheckForNull
    @Attribute(required = false)
    private String hostId;
    @CheckForNull
    @Attribute(required = false)
    private String sheepId;
    @CheckForNull
    @Attribute(required = false)
    private String name;
    @Attribute(required = false)
    private long offset = 0;
    @Attribute(required = false)
    private int length = -1;
    @Attribute(required = false)
    private boolean random = false;
    @Attribute(required = false)
    private String pattern;

    @Override
    public void run(ControllerContext context) throws ControllerException, InterruptedException {
        Sheep sheep;
        if (sheepId != null) {
            sheep = toSheep(context, sheepId);
        } else if (hostId != null) {
            Host host = toHost(context, hostId);
            sheep = context.getSheep(host).values().iterator().next();
        } else {
            sheep = toSheep(context, null);
        }
        Vdi vdi = toVdi(context, name);

        long _offset;
        if (random)
            _offset = vdi.newOffset();
        else
            _offset = offset * 1024;

        int _length;
        if (length <= 0)
            _length = (int) vdi.getSize();
        else if (random)
            _length = vdi.newLength(_offset);
        else
            _length = length * 1024;

        byte[] data = null;
        if (pattern != null)
            data = EscapeUtils.unescape_perl_string(pattern);

        Host host = sheep.getHost();
        VdiReadOperator request = new VdiReadOperator(sheep.getConfig().getPort(), vdi.getName(), _offset, _length, data);
        context.execute(host, request);
    }
}
