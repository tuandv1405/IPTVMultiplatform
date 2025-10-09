package tss.t.tsiptv.core.firebase.firestore

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import tss.t.tsiptv.core.firebase.IFirebaseFirestore
import tss.t.tsiptv.core.firebase.exceptions.FirebaseFirestoreException

/**
 * A simple in-memory implementation of IFirebaseFirestore.
 * This implementation doesn't actually interact with Firebase, but provides a basic structure
 * that can be extended by platform-specific implementations.
 */
class InMemoryFirebaseFirestore : IFirebaseFirestore {
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