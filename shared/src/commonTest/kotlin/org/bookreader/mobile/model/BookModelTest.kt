package org.bookreader.mobile.model

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class BookModelTest {
    @Test
    fun managedPathCannotBeSourceUriAbsolutePathOrTraversal() {
        listOf("content://provider/123", "file:///private/book", "/absolute/book", "C:/book", "books/../book", "books/./book", "books//book", "books\\book", "books/\u0000book")
            .forEach { assertFalse(isManagedRelativePath(it), it) }
        assertTrue(isManagedRelativePath("books/550e8400-e29b-41d4-a716-446655440000/original"))
    }
}
