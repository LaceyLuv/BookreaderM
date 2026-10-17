package org.bookreader.mobile.reader

import org.junit.Assert.*
import org.junit.Test

class ReaderSeekPreviewTest {
    @Test fun cancelledDragAndLateFinishNeverCommitPreview() {
        val commits = mutableListOf<Float>()
        val seek = ReaderSeekPreview(commits::add)
        seek.dragStarted()
        seek.change(.8f)
        val version = seek.version
        seek.cancel()
        seek.finishNonDrag(version)
        seek.dragStopped()
        assertNull(seek.fraction)
        assertTrue(commits.isEmpty())
    }

    @Test fun releaseCommitsOnceAndNextAccessibleActionCanCommit() {
        val commits = mutableListOf<Float>()
        val seek = ReaderSeekPreview(commits::add)
        seek.dragStarted()
        seek.change(.7f)
        val version = seek.version
        seek.dragStopped()
        seek.finishNonDrag(version)
        assertEquals(listOf(.7f), commits)
        seek.change(.2f)
        seek.finishNonDrag(seek.version)
        assertEquals(listOf(.7f, .2f), commits)
        assertNull(seek.fraction)
    }

    @Test fun replacedPreviewRejectsOldFinishAndDismissDiscardsNewValue() {
        val commits = mutableListOf<Float>()
        val seek = ReaderSeekPreview(commits::add)
        seek.change(.3f)
        val old = seek.version
        seek.change(.9f)
        seek.finishNonDrag(old)
        assertTrue(commits.isEmpty())
        seek.cancel()
        seek.finishNonDrag(seek.version)
        assertTrue(commits.isEmpty())
    }
}
