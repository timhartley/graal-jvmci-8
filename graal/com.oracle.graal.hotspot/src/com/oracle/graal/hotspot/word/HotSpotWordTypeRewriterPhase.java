/*
 * Copyright (c) 2014, 2014, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.hotspot.word;

import static com.oracle.graal.hotspot.word.HotSpotOperation.HotspotOpcode.*;

import com.oracle.graal.api.meta.*;
import com.oracle.graal.api.replacements.*;
import com.oracle.graal.compiler.common.*;
import com.oracle.graal.compiler.common.type.*;
import com.oracle.graal.graph.*;
import com.oracle.graal.hotspot.nodes.*;
import com.oracle.graal.hotspot.word.HotSpotOperation.HotspotOpcode;
import com.oracle.graal.nodes.*;
import com.oracle.graal.nodes.calc.*;
import com.oracle.graal.nodes.java.*;
import com.oracle.graal.nodes.type.*;
import com.oracle.graal.word.nodes.*;
import com.oracle.graal.word.phases.*;

public class HotSpotWordTypeRewriterPhase extends WordTypeRewriterPhase {

    private final ResolvedJavaType klassPointerType;
    private final ResolvedJavaType methodPointerType;

    public HotSpotWordTypeRewriterPhase(MetaAccessProvider metaAccess, SnippetReflectionProvider snippetReflection, ConstantReflectionProvider constantReflection, Kind wordKind) {
        super(metaAccess, snippetReflection, constantReflection, wordKind);
        this.klassPointerType = metaAccess.lookupJavaType(KlassPointer.class);
        this.methodPointerType = metaAccess.lookupJavaType(MethodPointer.class);
    }

    @Override
    protected void changeToWord(StructuredGraph graph, ValueNode node) {
        PointerType type = getPointerType(node);
        if (type != null) {
            node.setStamp(StampFactory.forPointer(type));
        } else {
            super.changeToWord(graph, node);
        }
    }

    protected PointerType getPointerType(ValueNode node) {
        return getPointerType(StampTool.typeOrNull(node));
    }

    protected PointerType getPointerType(ResolvedJavaType type) {
        if (type != null) {
            if (klassPointerType.isAssignableFrom(type)) {
                return PointerType.Type;
            } else if (methodPointerType.isAssignableFrom(type)) {
                return PointerType.Method;
            }
        }
        return null;
    }

    @Override
    protected void rewriteAccessIndexed(StructuredGraph graph, AccessIndexedNode node) {
        if (node.stamp() instanceof PointerStamp && node instanceof LoadIndexedNode && node.elementKind() != Kind.Illegal) {
            /*
             * Prevent rewriting of the PointerStamp in the CanonicalizerPhase.
             */
            graph.replaceFixedWithFixed(node, graph.add(LoadIndexedPointerNode.create(node.stamp(), node.array(), node.index())));
        } else {
            super.rewriteAccessIndexed(graph, node);
        }
    }

    @Override
    protected void rewriteInvoke(StructuredGraph graph, MethodCallTargetNode callTargetNode) {
        ResolvedJavaMethod targetMethod = callTargetNode.targetMethod();
        HotSpotOperation operation = targetMethod.getAnnotation(HotSpotOperation.class);
        if (operation == null) {
            super.rewriteInvoke(graph, callTargetNode);
        } else {
            Invoke invoke = callTargetNode.invoke();
            NodeInputList<ValueNode> arguments = callTargetNode.arguments();

            switch (operation.opcode()) {
                case POINTER_EQ:
                case POINTER_NE:
                    assert arguments.size() == 2;
                    replace(invoke, pointerComparisonOp(graph, operation.opcode(), arguments.get(0), arguments.get(1)));
                    break;

                case FROM_POINTER:
                    assert arguments.size() == 1;
                    WordCastNode ptrToWord = graph.add(WordCastNode.pointerToWord(arguments.get(0), wordKind));
                    graph.addBeforeFixed(invoke.asNode(), ptrToWord);
                    replace(invoke, ptrToWord);
                    break;

                case TO_KLASS_POINTER:
                    assert arguments.size() == 1;
                    replaceToPointerOp(graph, invoke, PointerType.Type, arguments.get(0));
                    break;

                case TO_METHOD_POINTER:
                    assert arguments.size() == 1;
                    replaceToPointerOp(graph, invoke, PointerType.Method, arguments.get(0));
                    break;

                default:
                    throw GraalInternalError.shouldNotReachHere("unknown operation: " + operation.opcode());
            }
        }
    }

    private void replaceToPointerOp(StructuredGraph graph, Invoke invoke, PointerType type, ValueNode word) {
        WordCastNode wordToObject = graph.add(WordCastNode.wordToPointer(word, wordKind, type));
        graph.addBeforeFixed(invoke.asNode(), wordToObject);
        replace(invoke, wordToObject);
    }

    private static ValueNode pointerComparisonOp(StructuredGraph graph, HotspotOpcode opcode, ValueNode left, ValueNode right) {
        assert left.stamp() instanceof PointerStamp && right.stamp() instanceof PointerStamp;
        assert opcode == POINTER_EQ || opcode == POINTER_NE;

        PointerEqualsNode comparison = graph.unique(PointerEqualsNode.create(left, right));
        ValueNode eqValue = ConstantNode.forBoolean(opcode == POINTER_EQ, graph);
        ValueNode neValue = ConstantNode.forBoolean(opcode == POINTER_NE, graph);
        return graph.unique(ConditionalNode.create(comparison, eqValue, neValue));
    }
}