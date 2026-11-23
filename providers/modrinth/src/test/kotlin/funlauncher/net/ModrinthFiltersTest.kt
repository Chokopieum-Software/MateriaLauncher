package funlauncher.net

import kotlin.test.Test
import kotlin.test.assertEquals

class ModrinthFiltersTest {
    @Test fun convertsCatalogFiltersToModrinthSearchGroups() {
        val request = CatalogSearch("castle", "datapack", listOf("1.20", "1.21"),
            listOf("adventure"), listOf("fabric"), 20, 20, listOf("food"), listOf("forge"))
        assertEquals(listOf(listOf("project_type:mod"), listOf("categories:datapack"),
            listOf("versions:1.20", "versions:1.21"), listOf("categories:adventure"),
            listOf("categories!=food"), listOf("categories!=forge"), listOf("categories:fabric")),
            searchFacets(request))
    }
}
