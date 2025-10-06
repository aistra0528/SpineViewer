package org.anonymous.spineviewer

interface Platform {
    fun getCurrentFile(): String
    fun getCurrentBackground(): String?
    fun importFiles()
}
