package com.omai.neocalc.calculator

import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.listSaver
import java.math.BigDecimal

/**
 * Persists calculator state across configuration change and process death.
 *
 * The pending stack is flattened to a single string rather than a nested list,
 * because listSaver can only store types the Bundle understands and a list of
 * pairs is not one of them.
 */
val CalculatorStateSaver: Saver<CalculatorState, Any> = listSaver(
    save = { state ->
        listOf(
            state.display,
            state.stack.joinToString(FRAME) { "${it.value.toPlainString()}$FIELD${it.operator.name}" },
            state.brackets.joinToString(FRAME),
            state.entering,
            state.lastOperation?.first?.name,
            state.lastOperation?.second?.toPlainString(),
            state.error?.name,
            state.memory.toPlainString(),
            state.angleMode.name,
            state.computed,
        )
    },
    restore = { saved ->
        val lastOp = (saved[4] as String?)?.let(Operator::valueOf)
        val lastOperand = (saved[5] as String?)?.let(::BigDecimal)
        CalculatorState(
            display = saved[0] as String,
            stack = (saved[1] as String).split(FRAME).filter { it.isNotBlank() }.map { frame ->
                val (value, operator) = frame.split(FIELD)
                Pending(BigDecimal(value), Operator.valueOf(operator))
            },
            brackets = (saved[2] as String).split(FRAME)
                .filter { it.isNotBlank() }
                .map { it.toInt() },
            entering = saved[3] as Boolean,
            lastOperation = if (lastOp != null && lastOperand != null) lastOp to lastOperand else null,
            error = (saved[6] as String?)?.let(CalcError::valueOf),
            memory = BigDecimal(saved[7] as String),
            angleMode = AngleMode.valueOf(saved[8] as String),
            computed = saved[9] as Boolean,
        )
    },
)

// Separators that cannot occur in a BigDecimal or an enum name.
private const val FRAME = "|"
private const val FIELD = "~"
