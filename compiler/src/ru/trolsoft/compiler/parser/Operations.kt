package ru.trolsoft.compiler.parser

import ru.trolsoft.therat.lang.OperatorToken

enum class OperatorType {
    BINARY,
    UNARY,
    PREFIX
}

enum class Associativity {
    LEFT_TO_RIGHT,
    RIGHT_TO_LEFT
}

enum class Operator(val str: String, private val priority: Int, private val associativity: Associativity, val type: OperatorType = OperatorType.BINARY) {
    ASSIGN("=", 10, Associativity.RIGHT_TO_LEFT),
    PLUS_ASSIGN("+=", 10, Associativity.RIGHT_TO_LEFT),
    MINUS_ASSIGN("-=", 10, Associativity.RIGHT_TO_LEFT),
    MUL_ASSIGN("*=", 10, Associativity.RIGHT_TO_LEFT),
    DIV_ASSIGN("/=", 10, Associativity.RIGHT_TO_LEFT),
    MOD_ASSIGN("%=", 10, Associativity.RIGHT_TO_LEFT),
    SHL_ASSIGN("<<=", 10, Associativity.RIGHT_TO_LEFT),
    SHR_ASSIGN(">>=", 10, Associativity.RIGHT_TO_LEFT),
    AND_ASSIGN("&=", 10, Associativity.RIGHT_TO_LEFT),
    XOR_ASSIGN("^=", 10, Associativity.RIGHT_TO_LEFT),
    OR_ASSIGN("|=", 10, Associativity.RIGHT_TO_LEFT),

    LOGICAL_OR("||", 20, Associativity.LEFT_TO_RIGHT),

    LOGICAL_AND("&&", 30, Associativity.LEFT_TO_RIGHT),

    OR("|", 40, Associativity.LEFT_TO_RIGHT),
    XOR("^", 50, Associativity.LEFT_TO_RIGHT),
    AND("&", 60, Associativity.LEFT_TO_RIGHT),

    EQ("==", 70, Associativity.LEFT_TO_RIGHT),
    NOT_EQ("!=", 70, Associativity.LEFT_TO_RIGHT),

    LESS("<", 80, Associativity.LEFT_TO_RIGHT),
    GREAT(">", 80, Associativity.LEFT_TO_RIGHT),
    LESS_EQ("<=", 80, Associativity.LEFT_TO_RIGHT),
    GREAT_EQ(">=", 80, Associativity.LEFT_TO_RIGHT),

    SHL("<<", 90, Associativity.LEFT_TO_RIGHT),
    SHR(">>", 90, Associativity.LEFT_TO_RIGHT),

    PLUS("+", 100, Associativity.LEFT_TO_RIGHT),
    MINUS("-", 100, Associativity.LEFT_TO_RIGHT),

    MUL("*", 110, Associativity.LEFT_TO_RIGHT),
    DIV("/", 110, Associativity.LEFT_TO_RIGHT),
    MOD("%", 110, Associativity.LEFT_TO_RIGHT),

    BINARY_NOT("~", 120, Associativity.RIGHT_TO_LEFT, OperatorType.PREFIX),
    LOGICAL_NOT("!", 120, Associativity.RIGHT_TO_LEFT, OperatorType.PREFIX),

    INC("++", 130, Associativity.LEFT_TO_RIGHT, OperatorType.UNARY),
    DEC("--", 130, Associativity.LEFT_TO_RIGHT, OperatorType.UNARY),

//    DOT(".", 130, Associativity.LEFT_TO_RIGHT),
    ARROW_RIGHT("->", 130, Associativity.LEFT_TO_RIGHT);


    fun isMorePriorityThanRight(other: Operator): Boolean {
        if (priority > other.priority) {
            return true
        } else if (priority < other.priority) {
            return false
        }
        return associativity != Associativity.RIGHT_TO_LEFT
    }

    fun isMorePriorityThanLeft(other: Operator): Boolean {
        if (priority > other.priority) {
            return true
        } else if (priority < other.priority) {
            return false
        }
        return associativity == Associativity.RIGHT_TO_LEFT
    }

    fun isBinary(): Boolean = type == OperatorType.BINARY
    fun isPrefix(): Boolean = type == OperatorType.PREFIX || type == OperatorType.UNARY
    fun isSuffix(): Boolean = type == OperatorType.UNARY
    fun isMinusOrPlus(): Boolean = this == PLUS || this == MINUS
    fun isSelfAssign(): Boolean = this == PLUS_ASSIGN || this == MINUS_ASSIGN || this == MUL_ASSIGN ||
            this == DIV_ASSIGN || this == MOD_ASSIGN || this == SHL_ASSIGN || this == SHR_ASSIGN ||
            this == AND_ASSIGN || this == XOR_ASSIGN || this == OR_ASSIGN


    companion object {
        fun fromToken(t: OperatorToken): Operator {
            for (v in values()) {
                if (v.str == t.name) {
                    return v
                }
            }
            errorOperatorExpected(t)
        }
    }
}

