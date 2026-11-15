import io.github.proify.extensions.json
import io.github.proify.lyricon.qishuiprovider.xposed.parser.NetResponseCache
import io.github.proify.lyricon.qishuiprovider.xposed.parser.toRichLyric
import org.junit.Test

class Parser {

    @Test
    fun test() {
        val jsonData = readFile("2.json")
        val je = json.decodeFromString<NetResponseCache>(jsonData)

        val line = je.toRichLyric()
        line.forEach {
            println(it)
        }

    }

    fun readFile(name: String): String {
        return Parser::class.java.getResourceAsStream(name).use {
            it?.bufferedReader()?.readText() as String
        }
    }
}