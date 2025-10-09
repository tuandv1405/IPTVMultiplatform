package tss.t.tsiptv.core.firebase

import kotlinx.coroutines.flow.Flow

/**
 * Interface for Firebase Firestore.
 * This is a platform-independent interface that will have platform-specific implementations.
 */
interface IFirebaseFirestore {
    /**
     * Gets a document from a collection.
     *
     * @param collection The collection to get the document from
     * @param documentId The ID of the document to get
     * @return The document as a map of field names to values, or null if the document doesn't exist
     */
    suspend fun getDocument(collection: String, documentId: String): Map<String, Any>?

    /**
     * Gets all documents from a collection.
     *
     * @param collection The collection to get documents from
     * @return A list of documents, each as a map of field names to values
     */
    suspend fun getDocuments(collection: String): List<Map<String, Any>>

    /**
     * Observes a document from a collection.
     *
     * @param collection The collection to observe the document from
     * @param documentId The ID of the document to observe
     * @return A flow that emits the document as a map of field names to values, or null if the document doesn't exist
     */
    fun observeDocument(collection: String, documentId: String): Flow<Map<String, Any>?>

    /**
     * Observes all documents from a collection.
     *
     * @param collection The collection to observe documents from
     * @return A flow that emits a list of documents, each as a map of field names to values
     */
    fun observeDocuments(collection: String): Flow<List<Map<String, Any>>>

    /**
     * Sets a document in a collection.
     *
     * @param collection The collection to set the document in
     * @param documentId The ID of the document to set
     * @param data The document data as a map of field names to values
     */
    suspend fun setDocument(collection: String, documentId: String, data: Map<String, Any>)

    /**
     * Updates a document in a collection.
     *
     * @param collection The collection to update the document in
     * @param documentId The ID of the document to update
     * @param data The fields to update as a map of field names to values
     * @throws tss.t.tsiptv.core.firebase.exceptions.FirebaseFirestoreException if the document doesn't exist
     */
    suspend fun updateDocument(collection: String, documentId: String, data: Map<String, Any>)

    /**
     * Deletes a document from a collection.
     *
     * @param collection The collection to delete the document from
     * @param documentId The ID of the document to delete
     */
    suspend fun deleteDocument(collection: String, documentId: String)

    /**
     * Adds a document to a collection with an auto-generated ID.
     *
     * @param collection The collection to add the document to
     * @param data The document data as a map of field names to values
     * @return The ID of the newly created document
     */
    suspend fun addDocument(collection: String, data: Map<String, Any>): String

    /**
     * Queries documents from a collection.
     *
     * @param collection The collection to query documents from
     * @param field The field to filter on
     * @param operator The operator to use for filtering (e.g., "==", ">", "<")
     * @param value The value to compare against
     * @return A list of documents matching the query, each as a map of field names to values
     */
    suspend fun queryDocuments(
        collection: String,
        field: String,
        operator: String,
        value: Any
    ): List<Map<String, Any>>

    /**
     * Observes a query on documents from a collection.
     *
     * @param collection The collection to query documents from
     * @param field The field to filter on
     * @param operator The operator to use for filtering (e.g., "==", ">", "<")
     * @param value The value to compare against
     * @return A flow that emits a list of documents matching the query, each as a map of field names to values
     */
    fun observeQueryDocuments(
        collection: String,
        field: String,
        operator: String,
        value: Any
    ): Flow<List<Map<String, Any>>>
}

