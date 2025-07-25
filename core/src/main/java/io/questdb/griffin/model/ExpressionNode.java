/*******************************************************************************
 *     ___                  _   ____  ____
 *    / _ \ _   _  ___  ___| |_|  _ \| __ )
 *   | | | | | | |/ _ \/ __| __| | | |  _ \
 *   | |_| | |_| |  __/\__ \ |_| |_| | |_) |
 *    \__\_\\__,_|\___||___/\__|____/|____/
 *
 *  Copyright (c) 2014-2019 Appsicle
 *  Copyright (c) 2019-2024 QuestDB
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 *
 ******************************************************************************/

package io.questdb.griffin.model;

import io.questdb.griffin.OperatorExpression;
import io.questdb.griffin.OperatorRegistry;
import io.questdb.griffin.SqlKeywords;
import io.questdb.std.Chars;
import io.questdb.std.Mutable;
import io.questdb.std.Numbers;
import io.questdb.std.ObjList;
import io.questdb.std.ObjectFactory;
import io.questdb.std.ObjectPool;
import io.questdb.std.str.CharSink;
import io.questdb.std.str.Sinkable;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;

public class ExpressionNode implements Mutable, Sinkable {

    public static final int ARRAY_ACCESS = 1;
    public static final int ARRAY_CONSTRUCTOR = ARRAY_ACCESS + 1;
    public static final int BIND_VARIABLE = ARRAY_CONSTRUCTOR + 1;
    public static final int CONSTANT = BIND_VARIABLE + 1;
    public static final int CONTROL = CONSTANT + 1;
    public static final int FUNCTION = CONTROL + 1;
    public static final int LITERAL = FUNCTION + 1;
    public static final int MEMBER_ACCESS = LITERAL + 1;
    public static final int OPERATION = MEMBER_ACCESS + 1;
    public static final int QUERY = OPERATION + 1;
    public static final int SET_OPERATION = QUERY + 1;
    public static final ExpressionNodeFactory FACTORY = new ExpressionNodeFactory();
    public static final int UNKNOWN = 0;
    public final ObjList<ExpressionNode> args = new ObjList<>(4);
    public boolean innerPredicate = false;
    public int intrinsicValue = IntrinsicModel.UNDEFINED;
    public ExpressionNode lhs;
    public int paramCount;
    public int position;
    public int precedence;
    public QueryModel queryModel;
    public ExpressionNode rhs;
    public CharSequence token;
    public int type;

    // IMPORTANT: update deepClone method after adding a new field
    private ExpressionNode() {
    }

    public static boolean compareNodesExact(ExpressionNode a, ExpressionNode b) {
        if (a == null && b == null) {
            return true;
        }
        if (a == null || b == null || a.type != b.type) {
            return false;
        }
        return (a.type == FUNCTION || a.type == LITERAL ? Chars.equalsIgnoreCase(a.token, b.token) : Chars.equals(a.token, b.token))
                && compareArgsExact(a, b);
    }

    public static boolean compareNodesGroupBy(
            ExpressionNode groupByExpr,
            ExpressionNode columnExpr,
            QueryModel translatingModel
    ) {
        if (groupByExpr == null && columnExpr == null) {
            return true;
        }

        if (groupByExpr == null || columnExpr == null || groupByExpr.type != columnExpr.type) {
            return false;
        }

        if (!Chars.equals(groupByExpr.token, columnExpr.token)) {
            int index = translatingModel.getAliasToColumnMap().keyIndex(columnExpr.token);
            if (index > -1) {
                return false;
            }

            final QueryColumn qc = translatingModel.getAliasToColumnMap().valueAt(index);
            final CharSequence tok = groupByExpr.token;
            final CharSequence qcTok = qc.getAst().token;
            if (Chars.equals(qcTok, tok)) {
                return true;
            }

            int dot = Chars.indexOfLastUnquoted(tok, '.');
            if (dot > -1
                    && translatingModel.getModelAliasIndex(tok, 0, dot) > -1
                    && Chars.equals(qcTok, tok, dot + 1, tok.length())) {
                return compareArgs(groupByExpr, columnExpr, translatingModel);
            }

            return false;
        }

        return compareArgs(groupByExpr, columnExpr, translatingModel);
    }

    public static ExpressionNode deepClone(final ObjectPool<ExpressionNode> pool, final ExpressionNode node) {
        if (node == null) {
            return null;
        }
        ExpressionNode copy = pool.next();
        for (int i = 0, n = node.args.size(); i < n; i++) {
            copy.args.add(ExpressionNode.deepClone(pool, node.args.get(i)));
        }
        copy.token = node.token;
        copy.queryModel = node.queryModel;
        copy.precedence = node.precedence;
        copy.position = node.position;
        copy.lhs = ExpressionNode.deepClone(pool, node.lhs);
        copy.rhs = ExpressionNode.deepClone(pool, node.rhs);
        copy.type = node.type;
        copy.paramCount = node.paramCount;
        copy.intrinsicValue = node.intrinsicValue;
        copy.innerPredicate = node.innerPredicate;
        return copy;
    }

    public void clear() {
        args.clear();
        token = null;
        precedence = 0;
        position = 0;
        lhs = null;
        rhs = null;
        type = UNKNOWN;
        paramCount = 0;
        intrinsicValue = IntrinsicModel.UNDEFINED;
        queryModel = null;
        innerPredicate = false;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ExpressionNode that = (ExpressionNode) o;
        return precedence == that.precedence
                && position == that.position
                && type == that.type
                && paramCount == that.paramCount
                && intrinsicValue == that.intrinsicValue
                && innerPredicate == that.innerPredicate
                && Objects.equals(args, that.args)
                && Objects.equals(token, that.token)
                && Objects.equals(queryModel, that.queryModel)
                && Objects.equals(lhs, that.lhs)
                && Objects.equals(rhs, that.rhs);
    }

    @Override
    public int hashCode() {
        return Objects.hash(args, token, queryModel, precedence, position, lhs, rhs, type, paramCount, intrinsicValue, innerPredicate);
    }

    public boolean isWildcard() {
        return type == LITERAL && Chars.endsWith(token, '*');
    }

    public boolean noLeafs() {
        return lhs == null || rhs == null;
    }

    public ExpressionNode of(int type, CharSequence token, int precedence, int position) {
        clear();
        // override literal with bind variable
        if (
                type == LITERAL
                        && token != null
                        && token.length() != 0
                        && ((token.charAt(0) == '$' && Numbers.isDecimal(token, 1)) || token.charAt(0) == ':')
        ) {
            this.type = BIND_VARIABLE;
        } else {
            this.type = type;
        }
        this.precedence = precedence;
        this.token = token;
        this.position = position;
        return this;
    }

    @Override
    public void toSink(@NotNull CharSink<?> sink) {
        // note: it's safe to take any registry (new or old) because we don't use precedence here
        OperatorRegistry registry = OperatorExpression.getRegistry();
        char openBracket = '(';
        char closeBracket = ')';
        if (token != null && SqlKeywords.isArrayKeyword(token)) {
            openBracket = '[';
            closeBracket = ']';
        }

        switch (paramCount) {
            case 0:
                if (queryModel != null) {
                    sink.putAscii('(').put(queryModel).putAscii(')');
                } else {
                    sink.put(token);
                    if (type == FUNCTION) {
                        sink.putAscii("()");
                    }
                }
                break;
            case 1:
                sink.put(token);
                sink.putAscii(openBracket);
                toSink(sink, rhs);
                sink.putAscii(closeBracket);
                break;
            case 2:
                if (registry.isOperator(token)) {
                    // an operator child might have an higher precedence than the parent
                    // if it was wrapped in parentheses.
                    final boolean lhsParent = lhs.type == OPERATION && lhs.precedence > precedence;
                    if (lhsParent) {
                        sink.putAscii('(');
                    }
                    toSink(sink, lhs);
                    if (lhsParent) {
                        sink.putAscii(')');
                    }
                    sink.putAscii(' ');
                    sink.put(token);
                    sink.putAscii(' ');
                    final boolean rhsParent = rhs.type == OPERATION && rhs.precedence >= precedence;
                    if (rhsParent) {
                        sink.putAscii('(');
                    }
                    toSink(sink, rhs);
                    if (rhsParent) {
                        sink.putAscii(')');
                    }
                } else if (token.length() == 2 && token.charAt(0) == '[' && token.charAt(1) == ']') {
                    // for array dereference we want to display them as lhs[rhs] instead of [](lhs, rhs)
                    sink.put(lhs);
                    sink.put('[');
                    sink.put(rhs);
                    sink.put(']');
                } else if (SqlKeywords.isCaseKeyword(token)) {
                    // for case we want to display them as 'case when lhs then rhs end' instead of case(lhs, rhs)
                    sink.put("case when ");
                    sink.put(lhs);
                    sink.put(" then ");
                    sink.put(rhs);
                    sink.put(" end");
                } else if (SqlKeywords.isCastKeyword(token)) {
                    // for cast we want to display them as lhs::rhs instead of cast(lhs, rhs)
                    // in some cases the casted parameter may contains space which makes it hard to understand when the
                    // cast is applied, in such case we wrap lhs in parentheses.
                    final boolean parent = lhs.type == OPERATION || SqlKeywords.isCaseKeyword(lhs.token) || SqlKeywords.isBetweenKeyword(lhs.token);
                    if (parent) {
                        sink.put('(');
                        sink.put(lhs);
                        sink.put(')');
                    } else {
                        sink.put(lhs);
                    }
                    sink.put(':');
                    sink.put(':');
                    sink.put(rhs);
                } else {
                    sink.put(token);
                    sink.putAscii(openBracket);
                    toSink(sink, lhs);
                    sink.putAscii(',');
                    sink.putAscii(' ');
                    toSink(sink, rhs);
                    sink.putAscii(closeBracket);
                }
                break;
            default:
                int n = args.size();
                if (registry.isOperator(token) && n > 0) {
                    // special case for "in"
                    toSink(sink, args.getQuick(n - 1));
                    sink.putAscii(' ');
                    sink.put(token);
                    sink.putAscii(' ');
                    sink.putAscii('(');
                    for (int i = n - 2; i > -1; i--) {
                        if (i < n - 2) {
                            sink.putAscii(',');
                            sink.putAscii(' ');
                        }
                        toSink(sink, args.getQuick(i));
                    }
                    sink.putAscii(')');
                } else if (SqlKeywords.isCaseKeyword(token)) {
                    // For the case keyword we want to display it as 'case [when x then x-1] [else x] end'.
                    sink.put("case");
                    for (int i = n - 1; i > 0; i -= 2) {
                        sink.put(" when ");
                        sink.put(args.getQuick(i));
                        sink.put(" then ");
                        toSink(sink, args.getQuick(i - 1));
                    }
                    if (n % 2 == 1) {
                        sink.put(" else ");
                        toSink(sink, args.getQuick(0));
                    }
                    sink.put(" end");
                } else {
                    sink.put(token);
                    sink.putAscii(openBracket);
                    for (int i = n - 1; i > -1; i--) {
                        if (i < n - 1) {
                            sink.putAscii(',');
                            sink.putAscii(' ');
                        }
                        toSink(sink, args.getQuick(i));
                    }
                    sink.putAscii(closeBracket);
                }
                break;
        }
    }

    @Override
    public String toString() {
        return Objects.toString(token);
    }

    private static boolean compareArgs(
            ExpressionNode groupByExpr,
            ExpressionNode columnExpr,
            QueryModel translatingModel
    ) {
        final int groupByArgsSize = groupByExpr.args.size();
        final int selectNodeArgsSize = columnExpr.args.size();

        if (groupByArgsSize != selectNodeArgsSize) {
            return false;
        }

        if (groupByArgsSize < 3) {
            return compareNodesGroupBy(groupByExpr.lhs, columnExpr.lhs, translatingModel)
                    && compareNodesGroupBy(groupByExpr.rhs, columnExpr.rhs, translatingModel);
        }

        for (int i = 0; i < groupByArgsSize; i++) {
            if (!compareNodesGroupBy(groupByExpr.args.get(i), columnExpr.args.get(i), translatingModel)) {
                return false;
            }
        }
        return true;
    }

    private static boolean compareArgsExact(ExpressionNode a, ExpressionNode b) {
        final int groupByArgsSize = a.args.size();
        final int selectNodeArgsSize = b.args.size();

        if (groupByArgsSize != selectNodeArgsSize) {
            return false;
        }

        if (groupByArgsSize < 3) {
            return compareNodesExact(a.lhs, b.lhs) && compareNodesExact(a.rhs, b.rhs);
        }

        for (int i = 0; i < groupByArgsSize; i++) {
            if (!compareNodesExact(a.args.get(i), b.args.get(i))) {
                return false;
            }
        }
        return true;
    }

    private static void toSink(CharSink<?> sink, ExpressionNode e) {
        if (e == null) {
            sink.putAscii("null");
        } else {
            e.toSink(sink);
        }
    }

    public static final class ExpressionNodeFactory implements ObjectFactory<ExpressionNode> {
        @Override
        public ExpressionNode newInstance() {
            return new ExpressionNode();
        }
    }
}
