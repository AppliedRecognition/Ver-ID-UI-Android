package com.appliedrec.verid.ui2

import android.util.SparseArray
import com.appliedrec.verid.core2.session.VerIDSessionResult
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

object SessionResultStorage {

    private val results: SparseArray<VerIDSessionResult> = SparseArray()
    private val resultLock = ReentrantLock()

    fun addResult(result: VerIDSessionResult): Int {
        resultLock.withLock {
            val resultId = results.size()
            results.put(resultId, result)
            return resultId
        }
    }

    fun removeResult(resultId: Int): VerIDSessionResult? {
        resultLock.withLock {
            return results.get(resultId)?.let {
                results.remove(resultId)
                return it
            }
        }
    }
}