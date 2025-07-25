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

package io.questdb.griffin.engine.functions.constants;

import io.questdb.cairo.sql.Record;
import io.questdb.griffin.PlanSink;
import io.questdb.griffin.engine.functions.FloatFunction;
import io.questdb.std.Numbers;

public class FloatConstant extends FloatFunction implements ConstantFunction {
    public static final FloatConstant NULL = new FloatConstant(Float.NaN);
    private final float value;

    public FloatConstant(float value) {
        this.value = value;
    }

    public static FloatConstant newInstance(float value) {
        return Numbers.isFinite(value) ? new FloatConstant(value) : NULL;
    }

    @Override
    public float getFloat(Record rec) {
        return value;
    }

    @Override
    public boolean isNullConstant() {
        // NaN is used as a marker for NULL
        return Numbers.isNull(value);
    }

    @Override
    public void toPlan(PlanSink sink) {
        sink.val((double) value).val('f');
    }
}
