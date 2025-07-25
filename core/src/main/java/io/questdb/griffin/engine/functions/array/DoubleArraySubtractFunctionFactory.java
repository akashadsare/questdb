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

package io.questdb.griffin.engine.functions.array;

import io.questdb.cairo.CairoConfiguration;
import io.questdb.cairo.arr.ArrayView;
import io.questdb.cairo.sql.Function;
import io.questdb.griffin.FunctionFactory;
import io.questdb.griffin.SqlException;
import io.questdb.griffin.SqlExecutionContext;
import io.questdb.std.IntList;
import io.questdb.std.ObjList;
import io.questdb.std.Transient;

public class DoubleArraySubtractFunctionFactory implements FunctionFactory {

    @Override
    public String getSignature() {
        return "-(D[]D[])";
    }

    @Override
    public Function newInstance(
            int position,
            @Transient ObjList<Function> args,
            @Transient IntList argPositions,
            CairoConfiguration configuration,
            SqlExecutionContext sqlExecutionContext
    ) throws SqlException {
        return new Func(
                configuration,
                args.getQuick(0),
                args.getQuick(1),
                argPositions.getQuick(0)
        );
    }

    private static class Func extends DoubleArrayBinaryOperator {

        private Func(
                CairoConfiguration configuration,
                Function leftArg,
                Function rightArg,
                int leftArgPos
        ) {
            super("-", configuration, leftArg, rightArg, leftArgPos);
        }

        @Override
        protected double applyOperation(double leftVal, double rightVal) {
            return leftVal - rightVal;
        }

        @Override
        protected void bulkApplyOperation(ArrayView left, ArrayView right) {
            for (int i = 0, n = left.getFlatViewLength(); i < n; i++) {
                double leftVal = left.getDouble(i);
                double rightVal = right.getDouble(i);
                arrayOut.putDouble(i, leftVal - rightVal);
            }
        }
    }
}
