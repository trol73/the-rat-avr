package ru.trolsoft.therat.lang

inline fun extractCallArguments(
        maxArgs: Int?,
        openBracket: Token,
        nextToken: () -> Token?,
        onArgReady: (arg: List<Token>) -> Unit,
        onError: (t: Token, msg: String) -> Unit
) {
    val arg = mutableListOf<Token>()
    var bracketCode = 1
    var argNumber = 1
    while (true) {
        val token = nextToken()
        when {
            token == null -> {
                onError(openBracket, "Missing ')'")
                return
            }
            token.isBracket('(') -> {
                bracketCode++
                arg.add(token)
            }
            token.isBracket(')') -> {
                bracketCode--
                if (bracketCode == 0) {
                    if (!arg.isEmpty()) {
                        onArgReady(arg)
                    }
                    return
                } else {
                    arg.add(token)
                }
            }
            token is CommaToken -> {
                if (bracketCode == 1) {
                    onArgReady(arg)
                    arg.clear()
                    argNumber++
                    if (maxArgs != null && argNumber > maxArgs) {
                        onError(token, "Too many arguments")
                    }
                } else {
                    arg.add(token)
                }
            }
            else -> {
                arg.add(token)
            }
        }

    }

}

//    extractCallArguments(
//            { CommaToken(SourceLocation("", 1, 1)) },
//            { arg -> print("$arg") },
//            {t, msg ->  print("$t: $msg") }
//    )

