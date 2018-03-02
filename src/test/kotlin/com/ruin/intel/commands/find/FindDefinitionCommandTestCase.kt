package com.ruin.intel.commands.find

import com.ruin.intel.DUMMY_FILE_PATH
import com.ruin.intel.SUBCLASS_FILE_PATH
import com.ruin.intel.values.Position

class FindDefinitionCommandTestCase : FindDefinitionCommandTestBase() {
    fun `test finds superclass from subclass`() =
        checkFindsLocation(SUBCLASS_FILE_PATH,
            Position(5, 32), "SuperClass.java", Position(5, 22))

    fun `test finds declaration from usage`() =
        checkFindsLocation(DUMMY_FILE_PATH,
            Position(46, 23), "Dummy.java", Position(49, 15))

    fun `test finds declaration of class outside file`() =
        checkFindsLocation(DUMMY_FILE_PATH,
            Position(22, 9), "Problematic.java", Position(5, 13))
}