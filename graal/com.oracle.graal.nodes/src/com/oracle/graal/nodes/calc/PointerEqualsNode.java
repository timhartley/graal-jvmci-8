/*
 * Copyright (c) 2011, 2014, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */
package com.oracle.graal.nodes.calc;

import com.oracle.graal.compiler.common.calc.*;
import com.oracle.graal.compiler.common.type.*;
import com.oracle.graal.graph.spi.*;
import com.oracle.graal.nodeinfo.*;
import com.oracle.graal.nodes.*;
import com.oracle.graal.nodes.util.*;

@NodeInfo(shortName = "==")
public class PointerEqualsNode extends CompareNode {

    public PointerEqualsNode(ValueNode x, ValueNode y) {
        super(Condition.EQ, false, x, y);
        assert x.stamp() instanceof AbstractPointerStamp;
        assert y.stamp() instanceof AbstractPointerStamp;
    }

    @Override
    public ValueNode canonical(CanonicalizerTool tool, ValueNode forX, ValueNode forY) {
        if (GraphUtil.unproxify(forX) == GraphUtil.unproxify(forY)) {
            return LogicConstantNode.tautology();
        } else if (forX.stamp().alwaysDistinct(forY.stamp())) {
            return LogicConstantNode.contradiction();
        } else if (((AbstractPointerStamp) forX.stamp()).alwaysNull()) {
            return new IsNullNode(forY);
        } else if (((AbstractPointerStamp) forY.stamp()).alwaysNull()) {
            return new IsNullNode(forX);
        }
        return super.canonical(tool, forX, forY);
    }

    @Override
    protected CompareNode duplicateModified(ValueNode newX, ValueNode newY) {
        return new PointerEqualsNode(newX, newY);
    }
}