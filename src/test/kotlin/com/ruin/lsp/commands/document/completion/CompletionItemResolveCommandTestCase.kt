package com.ruin.lsp.commands.document.completion


/**
 * Hangs for some reason when all tests are run, but not when run individually
 */
//class CompletionItemResolveCommandTestCase : CompletionItemResolveCommandTestBase() {
//    fun `test resolves autoimport directive`() =
//        checkHasAdditionalEdits(Position(54, 11), "java.util.LinkedHashMap",
//            listOf(
//                TextEdit(range(3, 0, 3, 0), "import java.util.LinkedHashMap;\n")
//            )
//        )
//    fun `test doesn't import twice`() =
//        checkHasAdditionalEdits(Position(58, 11),"java.util.ArrayList", listOf())
//}