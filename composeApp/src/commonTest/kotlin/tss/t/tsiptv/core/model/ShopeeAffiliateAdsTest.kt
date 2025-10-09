package tss.t.tsiptv.core.model

import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class ShopeeAffiliateAdsTest {

    private val sampleJson = """
        {
            "total": 30,
            "data": [
                {
                    "subcategoryName": null,
                    "_id": "68acb96c5d9f63c401abd6da",
                    "productId": "ad-banner-20252608-02-06-1",
                    "title": "CODOBO Pure Set Sọc caro đỏ trắng",
                    "description": "🎀 PURE SET – DỄ THƯƠNG HẾT NẤC, NGỌT NGÀO NHƯ BÁNH KEM DÂU 🎀",
                    "price": 399990,
                    "productLink": "https://shopee.vn/CODOBO-Pure-Set-S%E1%BB%8Dc-caro-%C4%91%E1%BB%8F-tr%E1%BA%AFng-i.169756091.26482933307",
                    "qrCodeLink": "https://storage2.me-qr.com/qr/242422991.png?v=1756148738",
                    "ctaAction": "Mua Ngay",
                    "ctaUrl": "https://shopee.vn/CODOBO-Pure-Set-S%E1%BB%8Dc-caro-%C4%91%E1%BB%8F-tr%E1%BA%AFng-i.169756091.26482933307",
                    "sale": 12,
                    "salePrice": 349000,
                    "imageUrl": "https://down-vn.img.susercontent.com/file/vn-11134207-7ras8-mb7jh9t03q3ce6.webp",
                    "videoUrl": null,
                    "adsType": "product",
                    "productImages": [
                        "https://down-vn.img.susercontent.com/file/vn-11134207-7ras8-mb7jh9t03q3ce6.webp",
                        "https://down-vn.img.susercontent.com/file/vn-11134207-7ras8-mb7jh9xpwtlkaa.webp"
                    ],
                    "createdAt": "2025-08-25T19:28:44.009Z",
                    "updatedAt": "2025-08-31T17:24:39.010Z",
                    "bannerImage": null,
                    "categoryId": "68aea66d5ce442cd95a629d0",
                    "categoryName": "Clothes",
                    "subcategoryId": "68aea66d5ce442cd95a629d7"
                }
            ]
        }
    """.trimIndent()

    @Test
    fun testShopeeAffiliateAdsDeserialization() {
        val response = Json.decodeFromString<ShopeeAffiliateAdsResponse>(sampleJson)
        
        assertNotNull(response)
        assertEquals(30, response.total)
        assertNotNull(response.data)
        assertEquals(1, response.data?.size)
        
        val firstAd = response.data?.first()
        assertNotNull(firstAd)
        assertEquals("68acb96c5d9f63c401abd6da", firstAd.id)
        assertEquals("ad-banner-20252608-02-06-1", firstAd.productId)
        assertEquals("CODOBO Pure Set Sọc caro đỏ trắng", firstAd.title)
        assertEquals(399990L, firstAd.price)
        assertEquals(12, firstAd.sale)
        assertEquals(349000L, firstAd.salePrice)
        assertEquals("product", firstAd.adsType)
        assertEquals("Clothes", firstAd.categoryName)
        assertEquals(null, firstAd.subcategoryName)
        assertEquals(null, firstAd.videoUrl)
        assertEquals(null, firstAd.bannerImage)
        assertEquals(2, firstAd.productImages?.size)
    }

    @Test
    fun testShopeeAffiliateAdsWithNullFields() {
        val jsonWithNulls = """
            {
                "total": 1,
                "data": [
                    {
                        "_id": "test-id",
                        "title": "Test Product",
                        "price": null,
                        "sale": null,
                        "productImages": null,
                        "videoUrl": null,
                        "bannerImage": null,
                        "subcategoryName": null
                    }
                ]
            }
        """.trimIndent()
        
        val response = Json.decodeFromString<ShopeeAffiliateAdsResponse>(jsonWithNulls)
        
        assertNotNull(response)
        assertEquals(1, response.total)
        
        val firstAd = response.data?.first()
        assertNotNull(firstAd)
        assertEquals("test-id", firstAd.id)
        assertEquals("Test Product", firstAd.title)
        assertEquals(null, firstAd.price)
        assertEquals(null, firstAd.sale)
        assertEquals(null, firstAd.productImages)
        assertEquals(null, firstAd.videoUrl)
        assertEquals(null, firstAd.bannerImage)
        assertEquals(null, firstAd.subcategoryName)
    }
}