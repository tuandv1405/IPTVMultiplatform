package tss.t.tsiptv.core.firebase

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

/**
 * Interface for Firebase Firestore.
 * This is a platform-independent interface that will have platform-specific implementations.
 */
interface FirebaseFirestore {
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
     * @throws FirebaseFirestoreException if the document doesn't exist
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

/**
 * Exception thrown when a Firebase Firestore operation fails.
 *
 * @property code The error code
 * @property message The error message
 */
class FirebaseFirestoreException(val code: String, override val message: String) : Exception(message)

/**
 * A simple in-memory implementation of FirebaseFirestore.
 * This implementation doesn't actually interact with Firebase, but provides a basic structure
 * that can be extended by platform-specific implementations.
 */
class InMemoryFirebaseFirestore : FirebaseFirestore {
    private val collections = mutableMapOf<String, MutableMap<String, MutableStateFlow<Map<String, Any>>>>()

    override suspend fun getDocument(collection: String, documentId: String): Map<String, Any>? {
        return collections[collection]?.get(documentId)?.value
    }

    override suspend fun getDocuments(collection: String): List<Map<String, Any>> {
        return collections[collection]?.values?.map { it.value } ?: emptyList()
    }

    override fun observeDocument(collection: String, documentId: String): Flow<Map<String, Any>?> {
        val documentFlow = collections[collection]?.get(documentId)
        return documentFlow ?: MutableStateFlow(null)
    }

    override fun observeDocuments(collection: String): Flow<List<Map<String, Any>>> {
        val collectionMap = collections[collection]
        return if (collectionMap != null) {
            MutableStateFlow(collectionMap).map { it.values.map { flow -> flow.value } }
        } else {
            MutableStateFlow(emptyList())
        }
    }

    override suspend fun setDocument(collection: String, documentId: String, data: Map<String, Any>) {
        val collectionMap = collections.getOrPut(collection) { mutableMapOf() }
        val documentFlow = collectionMap[documentId]
        if (documentFlow != null) {
            documentFlow.value = data
        } else {
            collectionMap[documentId] = MutableStateFlow(data)
        }
    }

    override suspend fun updateDocument(collection: String, documentId: String, data: Map<String, Any>) {
        val documentFlow = collections[collection]?.get(documentId)
            ?: throw FirebaseFirestoreException(
                "not-found",
                "Document $documentId not found in collection $collection"
            )

        val updatedData = documentFlow.value.toMutableMap()
        updatedData.putAll(data)
        documentFlow.value = updatedData
    }

    override suspend fun deleteDocument(collection: String, documentId: String) {
        collections[collection]?.remove(documentId)
    }

    override suspend fun addDocument(collection: String, data: Map<String, Any>): String {
        val collectionMap = collections.getOrPut(collection) { mutableMapOf() }
        val documentId = "doc_${randomUUID()}_${collectionMap.size}"
        collectionMap[documentId] = MutableStateFlow(data)
        return documentId
    }

    /**
     * Generates a random UUID string.
     * This is a simple implementation that doesn't use platform-specific APIs.
     */
    private fun randomUUID(): String {
        val allowedChars = ('a'..'z') + ('A'..'Z') + ('0'..'9')
        return (1..20)
            .map { allowedChars.random() }
            .joinToString("")
    }

    override suspend fun queryDocuments(
        collection: String,
        field: String,
        operator: String,
        value: Any
    ): List<Map<String, Any>> {
        val collectionMap = collections[collection] ?: return emptyList()
        return collectionMap.values.map { it.value }.filter { document ->
            val fieldValue = document[field]
            when (operator) {
                "==" -> fieldValue == value
                ">" -> (fieldValue as? Comparable<Any>)?.compareTo(value) ?: 0 > 0
                "<" -> (fieldValue as? Comparable<Any>)?.compareTo(value) ?: 0 < 0
                ">=" -> (fieldValue as? Comparable<Any>)?.compareTo(value) ?: 0 >= 0
                "<=" -> (fieldValue as? Comparable<Any>)?.compareTo(value) ?: 0 <= 0
                "!=" -> fieldValue != value
                else -> false
            }
        }
    }

    override fun observeQueryDocuments(
        collection: String,
        field: String,
        operator: String,
        value: Any
    ): Flow<List<Map<String, Any>>> {
        val collectionMap = collections[collection]
        return if (collectionMap != null) {
            MutableStateFlow(collectionMap).map { map ->
                map.values.map { it.value }.filter { document ->
                    val fieldValue = document[field]
                    when (operator) {
                        "==" -> fieldValue == value
                        ">" -> (fieldValue as? Comparable<Any>)?.compareTo(value) ?: 0 > 0
                        "<" -> (fieldValue as? Comparable<Any>)?.compareTo(value) ?: 0 < 0
                        ">=" -> (fieldValue as? Comparable<Any>)?.compareTo(value) ?: 0 >= 0
                        "<=" -> (fieldValue as? Comparable<Any>)?.compareTo(value) ?: 0 <= 0
                        "!=" -> fieldValue != value
                        else -> false
                    }
                }
            }
        } else {
            MutableStateFlow(emptyList())
        }
    }
}
