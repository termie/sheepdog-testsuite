/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package com.nebula.sheeptester.target.operator;

import com.nebula.sheeptester.target.TargetContext;
import com.nebula.sheeptester.target.TargetException;
import com.nebula.sheeptester.target.exec.TargetProcess;
import java.io.IOException;

/**
 *
 * @author shevek
 */
public abstract class AbstractProcessOperator extends AbstractOperator {

    public static class ProcessResponse extends AbstractResponse {

        public ProcessResponse(AbstractOperator operator) {
            super(operator);
        }
    }

    @Override
    public Response run(TargetContext context) throws Exception {
        try {
            TargetProcess process = newProcess(context);
            process.execute();
            return new ProcessResponse(this);
        } catch (IOException e) {
            throw new TargetException(e);
        }
    }

    protected abstract TargetProcess newProcess(TargetContext context);
}