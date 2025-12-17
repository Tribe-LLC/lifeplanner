package az.tribe.lifeplanner.di

/**
 * Platform-specific file sharing interface
 */
interface FileSharer {
    /**
     * Share text content as a file
     * @param content The text content to share
     * @param fileName The suggested filename
     * @param mimeType The MIME type (default: application/json)
     */
    fun shareFile(content: String, fileName: String, mimeType: String = "application/json")

    /**
     * Copy text to clipboard
     */
    fun copyToClipboard(content: String)
}

/**
 * Platform-specific function to create FileSharer
 */
expect fun createFileSharer(): FileSharer
