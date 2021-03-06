
import assembler.Lexer
import assembler.parser.parseTokensToFile
import model.GlobalConfig
import java.io.File

/**
 * Starter for the Assemble segment of the build chain.
 *
 * Created by stephen on 11/28/15.
 */
fun main(args : Array<String>) {
    GlobalConfig.initDefaults()
    GlobalConfig.setProperty("verbose", true)
    GlobalConfig.setProperty("debug", true)

    val lexer = Lexer(File("src/hiAssembly.txt"))
    val tokenStream = lexer.lex()
    parseTokensToFile(tokenStream, File("HIcompilation.img"))
}