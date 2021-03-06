package assembler.parser

import assembler.*
import com.fox.general.PredicateTests.isTrue
import model.*
import java.util.*

/**
 * The logical functions that each handle a specific type of command: MOV(W/T/S), SUB, LDR, etc.
 *
 * These are not aggregated into a class, because that classes sole purpose would be to group these
 * functions. The behaviors are associated only in that they work on similar data. Thus, they are
 * in the same file.
 *
 * Created by stephen on 12/4/15.
 */

fun moveCommandParse(moveToken : MoveCommand, iterator : ReversibleIterator<Token>) : String {

    val registerDest = iterator.next() as RegisterToken
    val immOrRegToken = iterator.next()
    val workingImmediate = immOrRegToken is ImmediateToken
    var registerBit = if (workingImmediate) '1' else '0'
    val iteratorSize = iterator.size()
    val condition = moveToken.conditionInt.toBinaryString()
    val sBit = (if (moveToken.setSBit) '1' else '0')

    val builder = StringBuilder(32)

    Logger.v("Iterator.size = $iteratorSize")

    when (iteratorSize) {
        3 -> {
            val staticBits = ("00${registerBit}1" + (
                    if (!(moveToken.isMovT || moveToken.isMovW)) "101$sBit" // MOV(S)
                    else if (moveToken.isMovW) "0000" // MOVW
                    else "0100") /*MOVT*/)

            val immBinString = if (workingImmediate) {
                val immAtLeast12Token = immOrRegToken as ImmediateToken
                paddingCheck(immAtLeast12Token.value.toBinaryString(), 16)
            }
            else {
                val regToken = immOrRegToken as RegisterToken
                paddingCheck(regToken.nibble, 16)
            }

            val immList = splitImmediateString(immBinString, 4, 12)
            val imm4: String = immList[0]
            val imm12: String = immList[1]
            // this is the order of the bits for the move command.
            builder.append(condition)
                    .append(staticBits)
                    .append(imm4)
                    .append(registerDest.nibble)
                    .append(imm12)
        }
    }
    val toString = builder.toString()

    debugBinString(toString)

    return toString
}

/**
 * Takes in a Load or Store [DataOperationCommandToken], along with the rest of tokens that represent its
 * parameters.
 *
 * throw [IllegalStateException] if the passed arguments manage to be neither [LoadOperationToken] nor
 * [StoreOperationToken]
 */
fun loadStoreOperationParse(ldrStrToken : CommandToken, iterator : ReversibleIterator<Token>) : String {

    isTrue(ldrStrToken is LoadOperationToken || ldrStrToken is StoreOperationToken)

    val builder = StringBuilder(32)
    var PUWTriple = Triple('1', '1', '0') // Bits P, U, and W saying don't PUSHback, add UP and don't WRITEback

    val iteratorSize = iterator.size()
    var rBit = '0' // is working with a register for the 'value' that will be operated on
    val identBit = (if (ldrStrToken is LoadOperationToken) 1 else 0)
    val bit22 = '0'
    val condition = ldrStrToken.conditionInt.toBinaryString()

    Logger.v("Iterator.size = $iteratorSize")
    when (iteratorSize) {
        3 -> {
            // treat like a 4-token instruction, with '0' for an immediate value
            val registerDest = iterator.next() as RegisterToken
            val registerSource = iterator.next() as RegisterToken
            val staticBits = "01" + rBit + PUWTriple.first + PUWTriple.second + bit22 + PUWTriple.third + identBit
            builder.append(condition)
                    .append(staticBits)
                    .append(registerSource.nibble)
                    .append(registerDest.nibble)
            // pad the last imm12 bits with 0s
            builder.append("000000000000")
        }
        4 -> {
            // TODO: generate using the modified immediate constants, bit rotation on pg. 200/2734
        }
    }
    val toString = builder.toString()

    debugBinString(toString)

    return toString
}

fun addSubOperationParse(addSubToken : DataOperationCommandToken, iterator : ReversibleIterator<Token>) : String {
    if (addSubToken is AddOperationToken || addSubToken is SubtractOperationToken) {

        val builder = StringBuilder()
        val iteratorSize = iterator.size()
        var registerBit = '0' // TODO: change this bit later - when using registers for values
        val idPair = (if (addSubToken is AddOperationToken) "10" else "01")
        val sBit = (if (addSubToken.setSBit) '1' else '0')
        val registerDest = iterator.next() as RegisterToken
        val registerSource = iterator.next() as RegisterToken
        val condition = addSubToken.conditionInt.toBinaryString()

        when (iteratorSize) {
            4 -> {
                registerBit = '1' // in this case, '1' means immediate. Because ARM
                val imm12Token = iterator.next() as ImmediateToken
                // We're doing the immediate ADD/SUB
                val staticBits = "00" + registerBit + '0' + idPair + '0' + sBit
                builder.append(condition)
                        .append(staticBits)
                        .append(registerSource.nibble)
                        .append(registerDest.nibble)

                val immBinary = imm12Token.value.toBinaryString()

                builder.append(paddingCheck(immBinary, 12))
            }
        }

        val toString = builder.toString()

        debugBinString(toString)

        return toString
    } else {
        throw IllegalArgumentException("addSubToken parameter was not valid: $addSubToken")
    }
}

/**
 * Taking an [OrOperationToken] and the trailing tokens, outputs the binary for a supported
 * operation or errors out (to do)
 */
fun orOperationParse(orToken : OrOperationToken, iterator : ReversibleIterator<Token>) : String {
    // things go here
    val builder = StringBuilder(32)

    val condition = (orToken.conditionInt).toBinaryString()
    val registerDest = iterator.next() as RegisterToken
    val registerSource = iterator.next() as RegisterToken
    val sBit = (if (orToken.setSBit) '1' else '0')
    val nextToken = iterator.next()
    val regBit = if (nextToken is ImmediateToken) '1' else '0'
    val staticBits = "00${regBit}1100$sBit"

    val iteratorSize = iterator.size()

    Logger.d("Fixing on the iterator of size $iteratorSize")

    when (iteratorSize) {
        4 -> {
            // ORR{S}<c> <Rd>, <Rn>, #<const>
            val immToken = nextToken as ImmediateToken
            // now to deal with numbers that are too big
            val fixedTokenVal = modifiedConstantCheck(immToken)

            builder.append(condition)
                    .append(staticBits)
                    .append(registerSource.nibble)
                    .append(registerDest.nibble)
                    .append(fixedTokenVal)
        }
        5 -> {
            // ORR{S}<c> <Rd>, <Rn>, <Rm>{, <shift>}
        }
        6 -> {
            // ORR{S}<c> <Rd>, <Rn>, <Rm>, <type> <Rs>
        }
    }

    val toString = builder.toString()

    debugBinString(toString)

    return toString
}

fun branchOperationParse(branchToken : CommandToken, labelImmediateToken : Token) : String {
    isTrue(branchToken is BranchCommand || branchToken is BranchWithLinkCommand)

    val builder = StringBuilder()
    val conditionBinary = branchToken.conditionInt.toBinaryString()
    val condition = paddingCheck(conditionBinary, 4)
    val linkBit = if (branchToken is BranchWithLinkCommand) '1' else '0'
    val staticBits = "101$linkBit"

    if (labelImmediateToken is ImmediateToken) {     // we'll break in the other case. FOR NOW

        val immediateValue = labelImmediateToken.value
        val immediateBinary = paddingCheck(immediateValue.toBinaryString(), 24)

        Logger.d("Immediate as binary = $immediateBinary")
        Logger.d("Immediate binary length = ${immediateBinary.length}")

        return builder.append(condition)
                .append(staticBits)
                .append(immediateBinary)
                .toString()
    }
    else {
        Logger.e("immediateToken wasn't immediate: $labelImmediateToken")
        throw IllegalArgumentException("Non-immediate value was passed")
    }
}

fun pushPopOperationParse(stackOpToken: CommandToken, iterator: Iterator<Token>): String {
    isTrue(stackOpToken is PushManyToken || stackOpToken is PopManyToken)
    val condition = stackOpToken.conditionInt.toBinaryString()
    val getStatic12Bits: () -> String = pushPopFillerBits(stackOpToken) // for "lazy" evaluation
    val builder = StringBuilder()
            .append(condition)
            .append(getStatic12Bits())

    val registersToUse : MutableList<Int> = LinkedList()
    // get register numbers
    while (iterator.hasNext()) {
        try {
            val currentRegister = iterator.next() as RegisterToken
            registersToUse += currentRegister.registerNumber // aliases List.add
            Logger.v("Added register $currentRegister to register list")
        } catch(e: Exception) {
            throw IllegalStateException("Late syntax error. NON-REGISTER passed to stack encoding")
        }
    }
    val arrayOfRegisters = Array(16, { ind: Int ->
        if (registersToUse.contains(ind)) "1" else "0"
    })

    Logger.d("Array of register = ${arrayOfRegisters.joinToString()}")

    val registerString = arrayOfRegisters.reversedArray()
            .reduce { acc, curr -> acc.plus(curr) }

    Logger.d("The register string reduction = $registerString")

    builder.append(registerString)

    return builder.toString()
}

/**
 * Returns the 12, predetermined bits that fill in between the condition code and
 * the register list. As defined in armv7-a-r-manual.pdf, pg 535 & 6 of 2734
 */
private fun pushPopFillerBits(stackOpToken: CommandToken): () -> String {
    return {
        val lead3 = "100"
        val mid5 = if (stackOpToken is PopManyToken) "01011" else "10010"
        val lastFour = "1101"
        lead3.concat(mid5).concat(lastFour)
    }
}

fun paddingCheck(binary : String, capacity : Int) : String {
    var returnStr = (if (binary.length > capacity) {
        // assume "Modified Immediate Constants"
        binary.substring(binary.length - capacity)
    } else {
        // pad with zeros
        val shortBy = capacity - binary.length
        "0".repeat(shortBy) + binary
    })
    return returnStr
}

private fun debugBinString(binaryString : String) {
    val binAsInt = java.lang.Long.parseLong(binaryString, 2)
    val hexString = java.lang.Long.toHexString(binAsInt)
    Logger.d("Built binary $binAsInt, Hex: $hexString")
}

/**
 * Logic for building appropriate modified constants, for situations such as: ORR R3 R3 0x20000
 * Eventually, this will support the LabelTokens (that I've yet to make).
 */
fun modifiedConstantCheck(immToken : ImmediateToken) : String {
    // make a 8 bit value turn into a 32 bit value, with 4 bits for rotation
    isTrue(immToken.value > 0) // if the int flipped, we've got another problem

    var encoding = immToken.value
    val values = buildRotatedEncodings(encoding)

    if (values.size > 0) return values.last()

    throw ArithmeticException("The value ${immToken.value} couldn't be made to fit into 12 bits")
}

fun buildRotatedEncodings(encoding : Int) : ArrayList<String> {
    val values = ArrayList<String>()

    var encodedVal = encoding
    for (rotation in 0..31 step 2) {

        val maskEncoding = encodedVal and not(0xff)

        if (maskEncoding == 0 && encodedVal > 0) {
            println("encodedVal = $encodedVal")

            val rotatedBinary = (rotation / 2).toBinaryString()
            val properRotatedBinary = paddingCheck(rotatedBinary, 4)
            val fittedEncoding = encodedVal.toBinaryString()

            values += "${properRotatedBinary}${paddingCheck(fittedEncoding, 8)}"
        }

        encodedVal = encodedVal rotateLeft 2
    }

    Logger.d("fun is done")

    return values
}

fun splitImmediateString(binary : String, vararg chunkSizes : Int) : List<String> {
    isTrue(binary.length <= chunkSizes.sum())

    var currentIndex = 0
    val retList = ArrayList<String>()

    for (chunkSize in chunkSizes) {
        val strToAdd = binary.substring(currentIndex, chunkSize + currentIndex)
        currentIndex += chunkSize
        retList += strToAdd
    }

    if (currentIndex != binary.lastIndex) retList += binary.substring(currentIndex)

    return retList
}
