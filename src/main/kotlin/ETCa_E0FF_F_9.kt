import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.IntBuffer

const val P_OFFSET = 30
const val USE_OPCODE_SS_OFFSET = 29
const val SS_OFFSET = 27
const val CONDITION_OFFSET = 27
const val ARGA_OFFSET = 21
const val ARGB_OFFSET = 15
const val DEST_OFFSET = 9
const val OPCODE_OFFSET = 4
const val FLAG_OFFSET = 3
const val EOI_OFFSET = 2

const val SPECIAL_OPERATION_RESET = 1
const val SPECIAL_OPERATION_FLUSH_ALL = 2
const val SPECIAL_OPERATION_INVALIDATE_ALL = 4
const val SPECIAL_OPERATION_ALLOC_ZERO = 8
const val SPECIAL_OPERATION_DCACHE_INVALIDATE_LINE = 16
const val SPECIAL_OPERATION_DATA_PREFETCH_LINE = 32
const val SPECIAL_OPERATION_INSTRUCTION_PREFETCH_LINE = 64
const val SPECIAL_OPERATION_DCACHE_FLUSH_LINE = 128
const val SPECIAL_OPERATION_ICACHE_INVALIDATE_LINE = 256

enum class Version {
    ETCA_E0FF_F_9,
    ETCA_5E0FF_F_9
}

enum class MicroALUOpcode(val value: Int) {
    TEST(0),
    AND(1),
    OR(2),
    XOR(3),
    ADD(4),
    SUB(5),
    ADC(6),
    SBB(7),
    SHL(8),
    LSHR(9),
    ASHR(10),
    ROL(11),
    ROR(12),
    CMP(13),
    ZEXT(14),
    SEXT(15),
    LOAD(30),
    STORE(31),
    INVALID(-1)
}

enum class JumpCondition(val value: Int) {
    ZERO(0),
    EQUAL(0),
    NOT_ZERO(1),
    NOT_EQUAL(1),
    NEGATIVE(2),
    NOT_NEGATIVE(3),
    CARRY(4),
    BELOW(4),
    NO_CARRY(5),
    ABOVE_EQUAL(5),
    OVERFLOW(6),
    NO_OVERFLOW(7),
    BELOW_EQUAL(8),
    ABOVE(9),
    LESS(10),
    GREATER_EQUAL(11),
    LESS_EQUAL(12),
    GREATER(13),
    ALWAYS(14),
    NEVER(15)
}

enum class Register(val value: Int) {
    R0(0),
    A0(0),
    V0(0),
    R1(1),
    A1(1),
    V1(1),
    R2(2),
    A2(2),
    R3(3),
    S0(3),
    R4(4),
    S1(4),
    R5(5),
    BP(5),
    R6(6),
    SP(6),
    R7(7),
    LN(7),
    R8(8),
    T0(8),
    R9(9),
    T1(9),
    R10(10),
    T2(10),
    R11(11),
    T3(11),
    R12(12),
    T4(12),
    R13(13),
    S2(13),
    R14(14),
    S3(14),
    R15(15),
    S4(15),
    IP(16),
    FLAG(17),
    INT_IP(18),
    INT_SP(19),
    INT_MASK(20),
    INT_PENDING(21),
    INT_CAUSE(22),
    INT_DATA(23),
    INT_RET_IP(24),
    INT_RET_SP(25),
    PRIV(26),
    INT_RET_PRIV(27),
    NO_CACHE_START(28),
    NO_CACHE_END(29),
    ADDRESS_MODE(30),
    
    HALT_STATUS(42),
    SCRATCH_0(43),
    SCRATCH_1(44),
    SCRATCH_2(45),
    SCRATCH_3(46),
    HANDLING_INTERRUPT(47),
    
    REGA(48),
    REGB(49),
    REG_BASE(50),
    REGX(51),
    
    ADDRESS_WIDTH(53),
    IO_BUS_IDENTIFIER(54),
    OPERATION_WIDTH(55),
    SCALE_X(56),
    IMMEDIATE(57),
    LOG_OPERATION_SIZE(58),
    MEM_IMMEDIATE(59),
    NEXT_INSTR_ADDR(60),
    INSTR_SIZE(61),
    SPECIAL_OPERATIONS(62),
    INVALID(63)
}

enum class OperationSize(val value: Int) {
    HALF(0),
    WORD(1),
    DOUBLE(2),
    QUAD(3),
    DEFAULT(4),
    ADDRESS_WIDTH(5)
}

sealed class MicroOpcode {
    companion object {
        val NOP = RelJumpImm(Register.INVALID, JumpCondition.NEVER, 0)
    }
    
    data class ALURegOp(var opcode: MicroALUOpcode, var argA: Register, var argB: Register, var dest: Register, var updateFlags: Boolean = false, var endOfInstruction: Boolean = false, var updateIPToNextInstruction: Boolean = false, var operationSizeOverrideValue: OperationSize = OperationSize.DEFAULT): MicroOpcode() {
        override fun value(): Int {
            return (updateIPToNextInstruction.toInt() shl P_OFFSET) or (operationSizeOverrideValue.value shl SS_OFFSET) or (argA.value shl ARGA_OFFSET) or (argB.value shl ARGB_OFFSET) or (dest.value shl DEST_OFFSET) or (opcode.value shl OPCODE_OFFSET) or (updateFlags.toInt() shl FLAG_OFFSET) or (endOfInstruction.toInt() shl EOI_OFFSET)
        }
    }
    
    data class ALUImmOp(var opcode: MicroALUOpcode, var argA: Register, var immediate: Int, var dest: Register, var updateFlags: Boolean = false, var endOfInstruction: Boolean = false, var updateIPToNextInstruction: Boolean = false, var operationSizeOverrideValue: OperationSize = OperationSize.DEFAULT): MicroOpcode() {
        override fun value(): Int {
            return (updateIPToNextInstruction.toInt() shl P_OFFSET) or (operationSizeOverrideValue.value shl SS_OFFSET) or (argA.value shl ARGA_OFFSET) or immediate.alu() or (dest.value shl DEST_OFFSET) or (opcode.value shl OPCODE_OFFSET) or (updateFlags.toInt() shl FLAG_OFFSET) or (endOfInstruction.toInt() shl EOI_OFFSET)
        }
    }
    
    data class LoadConst(var dest: Register, var immediate: Int): MicroOpcode() {
        override fun value(): Int {
            return (dest.value shl DEST_OFFSET) or immediate.ldc()
        }
    }
    
    data class RelJumpImm(var conditionReg: Register, var condition: JumpCondition, var offset: Int): MicroOpcode() {
        override fun value(): Int {
            return (condition.value shl CONDITION_OFFSET) or (conditionReg.value shl ARGA_OFFSET) or offset.jmpImm()
        }
    }
    
    data class RelJumpReg(var conditionReg: Register, var condition: JumpCondition, var offsetReg: Register): MicroOpcode() {
        override fun value(): Int {
            return (condition.value shl CONDITION_OFFSET) or (conditionReg.value shl ARGA_OFFSET) or (offsetReg.value shl ARGB_OFFSET) or 0x00_00_02_03
        }
    }
    
    abstract fun value(): Int
}

enum class Opcode(val value: Int, val opcode: MicroALUOpcode, val flags: Boolean = false, val signExtend: Boolean = true, val store: Boolean = true) {
    ADD(0, MicroALUOpcode.ADD, flags = true),
    SUB(1, MicroALUOpcode.SUB, flags = true),
    RSUB(2, MicroALUOpcode.SUB, flags = true),
    CMP(3, MicroALUOpcode.SUB, flags = true, store = false),
    OR(4, MicroALUOpcode.OR, flags = true),
    XOR(5, MicroALUOpcode.XOR, flags = true),
    AND(6, MicroALUOpcode.AND, flags = true),
    TEST(7, MicroALUOpcode.AND, flags = true, store = false),
    MOVZ(8, MicroALUOpcode.ZEXT, signExtend = false),
    MOVS(9, MicroALUOpcode.SEXT),
    LOAD(10, MicroALUOpcode.LOAD, signExtend = false),
    STORE(11, MicroALUOpcode.STORE, signExtend = false, store = false),
    POP(12, MicroALUOpcode.INVALID, signExtend = false),
    PUSH(13, MicroALUOpcode.INVALID, signExtend = false, store = false),
    READCR(14, MicroALUOpcode.INVALID, signExtend = false),
    WRITECR(15, MicroALUOpcode.INVALID, signExtend = false, store = false),
    ADC(16, MicroALUOpcode.ADC, flags = true),
    SBB(17, MicroALUOpcode.SBB, flags = true),
    RSBB(18, MicroALUOpcode.SBB, flags = true),
    ROR(19, MicroALUOpcode.ROR, flags = true),
    SHL(20, MicroALUOpcode.SHL, flags = true),
    ROL(21, MicroALUOpcode.ROL, flags = true),
    SHR(22, MicroALUOpcode.LSHR, flags = true),
    ASHR(23, MicroALUOpcode.ASHR, flags = true),
    SLO(28, MicroALUOpcode.INVALID, signExtend = false),
    LEA(30, MicroALUOpcode.INVALID, signExtend = false);
    
    companion object {
        private val cache = Opcode.values()
        
        operator fun get(index: Int): Opcode? {
            return cache.firstOrNull {it.value == index}
        }
    }
}

data class MicroOpOffset(val name: String, val offsetMicroOps: Int) {
    companion object {
        val NONE = MicroOpOffset("", 0)
    }
    
    val offsetBytes = 4 * offsetMicroOps
}

val version = Version.ETCA_5E0FF_F_9

fun main() {
    val romPath = when(version) {
        Version.ETCA_E0FF_F_9 -> "/home/matthew/.local/share/godot/app_userdata/Turing Complete/schematics/component_factory/ETCA/ETCA_E0FF_F_9/Microcode/Microcode ROM/4370884270327232528.rom"
        Version.ETCA_5E0FF_F_9 -> "/home/matthew/.local/share/godot/app_userdata/Turing Complete/schematics/component_factory/ETCA/ETCA_5E0FF_F_9/Microcode/Microcode ROM/3777901440551170426.rom"
    }
    
    val rawMicrocode = ByteBuffer.allocate(32768)
    rawMicrocode.order(ByteOrder.LITTLE_ENDIAN)
    val microcode = rawMicrocode.asIntBuffer()
    
    // first 512 bytes of microcode rom (128 micro-ops)
    repeat(32) {
        val (code, offset) = regOpcode(it)
        if(offset.name.isNotEmpty())
            println("Reg-Reg Opcode '${offset.name}' at offset ${microcode.position() + offset.offsetMicroOps}")
        microcode.put(code)
    }
    // next 512 bytes of microcode rom (128 micro-ops)
    repeat(32) {
        val (code, offset) = immOpcode(it)
        if(offset.name.isNotEmpty())
            println("Reg-Imm Opcode '${offset.name}' at offset ${microcode.position() + offset.offsetMicroOps}")
        microcode.put(code)
    }
    // next 512 bytes of microcode rom (128 micro-ops)
    println("  readcr data at offset ${microcode.position()}")
    microcode.put(readCROpcode())
    // next 512 bytes of microcode rom (128 micro-ops)
    println("  writecr data at offset ${microcode.position()}")
    microcode.put(writeCROpcode())
    
    // new scope to prevent variable pollution
    run {
        // next 2048 bytes of microcode rom (512 micro-ops)
        val (code, offsets) = interrupts()
        for(offset in offsets) {
            println("Interrupt '${offset.name}' at offset ${microcode.position() + offset.offsetMicroOps}")
        }
        microcode.put(code)
    }
    
    // next 8192 bytes of microcode rom (2048 micro-ops)
    val boolList = listOf(false, true)
    for(sx in boolList) {
        for(b in boolList) {
            for(i in boolList) {
                val sibContent = mutableListOf<String>()
                if(sx)
                    sibContent.add("(x << s)")
                if(b)
                    sibContent.add("b")
                if(i)
                    sibContent.add("i")
                val sib = sibContent.joinToString(" + ", "[", "]")
                repeat(32) {
                    val (code, offset) = memImmOpcode(it, sx, b, i)
                    if(offset.name.isNotEmpty())
                        println("Mem-Imm Opcode '${offset.name} $sib, _' at offset ${microcode.position() + offset.offsetMicroOps}")
                    microcode.put(code)
                }
            }
        }
    }
    // next 8192 bytes of microcode rom (2048 micro-ops)
    for(sx in boolList) {
        for(b in boolList) {
            for(i in boolList) {
                val sibContent = mutableListOf<String>()
                if(sx)
                    sibContent.add("(x << s)")
                if(b)
                    sibContent.add("b")
                if(i)
                    sibContent.add("i")
                val sib = sibContent.joinToString(" + ", "[", "]")
                repeat(32) {
                    val (code, offset) = regMemOpcode(it, sx, b, i)
                    if(offset.name.isNotEmpty())
                        println("Reg-Mem Opcode '${offset.name} _, $sib' at offset ${microcode.position() + offset.offsetMicroOps}")
                    microcode.put(code)
                }
            }
        }
    }
    // next 8192 bytes of microcode rom (2048 micro-ops)
    for(sx in boolList) {
        for(b in boolList) {
            for(i in boolList) {
                val sibContent = mutableListOf<String>()
                if(sx)
                    sibContent.add("(x << s)")
                if(b)
                    sibContent.add("b")
                if(i)
                    sibContent.add("i")
                val sib = sibContent.joinToString(" + ", "[", "]")
                repeat(32) {
                    val (code, offset) = memRegOpcode(it, sx, b, i)
                    if(offset.name.isNotEmpty())
                        println("Mem-Reg Opcode '${offset.name} $sib, _' at offset ${microcode.position() + offset.offsetMicroOps}")
                    microcode.put(code)
                }
            }
        }
    }
    
    // final 4096 bytes of microcode rom (1024 micro-ops)
    run {
        val (code, offsets) = jumpOpcodes()
        for(offset in offsets) {
            println("Jump Opcode '${offset.name}' at offset ${microcode.position() + offset.offsetMicroOps}")
        }
        microcode.put(code)
    }
    run {
        val (code, offsets) = miscOpcodes()
        for(offset in offsets) {
            println("Opcode '${offset.name}' at offset ${microcode.position() + offset.offsetMicroOps}")
        }
        microcode.put(code)
    }
    
    //val pos = microcode.position()
    //for(i in 0 until pos)
    //    println("0x${microcode.get(i).toString(16).uppercase().padStart(8, '0')}")
    
    File(romPath).writeBytes(rawMicrocode.array())
}

fun regOpcode(opcode: Int): Pair<Array<MicroOpcode>, MicroOpOffset> {
    val microcodeArray = Array<MicroOpcode>(4) {MicroOpcode.NOP}
    val opcodeObj = Opcode[opcode] ?: return Pair(microcodeArray, MicroOpOffset.NONE)
    
    when(opcodeObj) {
        Opcode.MOVZ, Opcode.MOVS -> {
            microcodeArray[0] = MicroOpcode.ALURegOp(opcodeObj.opcode, Register.REGB, Register.LOG_OPERATION_SIZE, Register.REGA, endOfInstruction = true, updateIPToNextInstruction = true)
        }
        Opcode.PUSH -> {
            microcodeArray[0] = MicroOpcode.ALURegOp(MicroALUOpcode.SUB, Register.REGA, Register.OPERATION_WIDTH, Register.SCRATCH_0, operationSizeOverrideValue = OperationSize.ADDRESS_WIDTH)
            microcodeArray[1] = MicroOpcode.ALURegOp(MicroALUOpcode.STORE, Register.SCRATCH_0, Register.REGB, Register.INVALID)
            microcodeArray[2] = MicroOpcode.ALURegOp(MicroALUOpcode.SEXT, Register.SCRATCH_0, Register.ADDRESS_WIDTH, Register.REGA, endOfInstruction = true, updateIPToNextInstruction = true)
        }
        Opcode.POP -> {
            microcodeArray[0] = MicroOpcode.ALURegOp(MicroALUOpcode.LOAD, Register.REGB, Register.INVALID, Register.SCRATCH_0)
            microcodeArray[1] = MicroOpcode.ALURegOp(MicroALUOpcode.ADD, Register.REGB, Register.OPERATION_WIDTH, Register.REGB, operationSizeOverrideValue = OperationSize.ADDRESS_WIDTH)
            microcodeArray[2] = MicroOpcode.ALURegOp(MicroALUOpcode.SEXT, Register.SCRATCH_0, Register.LOG_OPERATION_SIZE, Register.REGA, endOfInstruction = true, updateIPToNextInstruction = true)
        }
        Opcode.RSUB, Opcode.RSBB -> {
            microcodeArray[0] = MicroOpcode.ALURegOp(opcodeObj.opcode, Register.REGB, Register.REGA, Register.REGA, updateFlags = opcodeObj.flags, endOfInstruction = true, updateIPToNextInstruction = true)
        }
        Opcode.LOAD, Opcode.STORE -> {
            val result = MicroOpcode.ALURegOp(opcodeObj.opcode, Register.REGB, Register.REGA, Register.INVALID, updateFlags = opcodeObj.flags, endOfInstruction = true, updateIPToNextInstruction = true)
        
            if(opcodeObj.store)
                result.dest = Register.REGA
        
            microcodeArray[0] = result
        }
        Opcode.SLO, Opcode.LEA, Opcode.READCR, Opcode.WRITECR -> {
            // not a valid instruction
            return Pair(microcodeArray, MicroOpOffset.NONE)
        }
        else -> {
            val result = MicroOpcode.ALURegOp(opcodeObj.opcode, Register.REGA, Register.REGB, Register.INVALID, updateFlags = opcodeObj.flags, endOfInstruction = true, updateIPToNextInstruction = true)
            
            if(opcodeObj.store)
                result.dest = Register.REGA
    
            microcodeArray[0] = result
        }
    }
    
    return Pair(microcodeArray, MicroOpOffset(opcodeObj.name.lowercase(), 0))
}

fun immOpcode(opcode: Int): Pair<Array<MicroOpcode>, MicroOpOffset> {
    val microcodeArray = Array<MicroOpcode>(4) {MicroOpcode.NOP}
    val opcodeObj = Opcode[opcode] ?: return Pair(microcodeArray, MicroOpOffset.NONE)
    
    when(opcodeObj) {
        Opcode.MOVZ, Opcode.MOVS -> {
            microcodeArray[0] = MicroOpcode.ALURegOp(opcodeObj.opcode, Register.IMMEDIATE, Register.LOG_OPERATION_SIZE, Register.REGA, endOfInstruction = true, updateIPToNextInstruction = true)
        }
        Opcode.PUSH -> {
            microcodeArray[0] = MicroOpcode.ALURegOp(MicroALUOpcode.SUB, Register.REGA, Register.OPERATION_WIDTH, Register.SCRATCH_0, operationSizeOverrideValue = OperationSize.ADDRESS_WIDTH)
            microcodeArray[1] = MicroOpcode.ALURegOp(MicroALUOpcode.STORE, Register.SCRATCH_0, Register.IMMEDIATE, Register.INVALID)
            microcodeArray[2] = MicroOpcode.ALURegOp(MicroALUOpcode.SEXT, Register.SCRATCH_0, Register.ADDRESS_WIDTH, Register.REGA, endOfInstruction = true, updateIPToNextInstruction = true)
        }
        Opcode.READCR -> {
            // jump by 68
            microcodeArray[0] = MicroOpcode.RelJumpImm(Register.INVALID, JumpCondition.ALWAYS, 72)
        }
        Opcode.WRITECR -> {
            // jump by 64 + 128
            microcodeArray[0] = MicroOpcode.RelJumpImm(Register.INVALID, JumpCondition.ALWAYS, 196)
        }
        Opcode.RSUB, Opcode.RSBB -> {
            microcodeArray[0] = MicroOpcode.ALURegOp(opcodeObj.opcode, Register.IMMEDIATE, Register.REGA, Register.REGA, updateFlags = opcodeObj.flags, endOfInstruction = true, updateIPToNextInstruction = true)
        }
        Opcode.LOAD, Opcode.STORE -> {
            val result = MicroOpcode.ALURegOp(opcodeObj.opcode, Register.IMMEDIATE, Register.REGA, Register.INVALID, updateFlags = opcodeObj.flags, endOfInstruction = true, updateIPToNextInstruction = true)
        
            if(opcodeObj.store)
                result.dest = Register.REGA
        
            microcodeArray[0] = result
        }
        Opcode.SLO -> {
            microcodeArray[0] = MicroOpcode.ALUImmOp(MicroALUOpcode.SHL, Register.REGA, 5, Register.REGA)
            microcodeArray[1] = MicroOpcode.ALURegOp(MicroALUOpcode.OR, Register.REGA, Register.IMMEDIATE, Register.REGA, endOfInstruction = true, updateIPToNextInstruction = true)
        }
        Opcode.LEA, Opcode.POP -> {
            // not a valid instruction
            return Pair(microcodeArray, MicroOpOffset.NONE)
        }
        else -> {
            val result = MicroOpcode.ALURegOp(opcodeObj.opcode, Register.REGA, Register.IMMEDIATE, Register.INVALID, updateFlags = opcodeObj.flags, endOfInstruction = true, updateIPToNextInstruction = true)
    
            if(opcodeObj.store)
                result.dest = Register.REGA
    
            microcodeArray[0] = result
        }
    }
    
    return Pair(microcodeArray, MicroOpOffset(opcodeObj.name.lowercase(), 0))
}

fun readCROpcode(): Array<MicroOpcode> {
    val microcodeArray = Array<MicroOpcode>(128) {MicroOpcode.NOP}
    
    microcodeArray[0] = MicroOpcode.ALUImmOp(MicroALUOpcode.CMP, Register.IMMEDIATE, 4, Register.SCRATCH_0, operationSizeOverrideValue = OperationSize.QUAD)
    microcodeArray[1] = MicroOpcode.RelJumpImm(Register.SCRATCH_0, JumpCondition.BELOW, 8) // skip privilege check
    microcodeArray[2] = MicroOpcode.ALUImmOp(MicroALUOpcode.CMP, Register.IMMEDIATE, 14, Register.SCRATCH_0, operationSizeOverrideValue = OperationSize.QUAD)
    microcodeArray[3] = MicroOpcode.RelJumpImm(Register.SCRATCH_0, JumpCondition.EQUAL, 6) // skip privilege check
    when(version) {
        Version.ETCA_E0FF_F_9 -> microcodeArray[4] = MicroOpcode.ALUImmOp(MicroALUOpcode.CMP, Register.IMMEDIATE, 16, Register.SCRATCH_0, operationSizeOverrideValue = OperationSize.QUAD)
        Version.ETCA_5E0FF_F_9 -> microcodeArray[4] = MicroOpcode.ALUImmOp(MicroALUOpcode.CMP, Register.IMMEDIATE, 17, Register.SCRATCH_0, operationSizeOverrideValue = OperationSize.QUAD)
    }
    microcodeArray[5] = MicroOpcode.RelJumpImm(Register.SCRATCH_0, JumpCondition.BELOW_EQUAL, 2) // privilege check jump
    // jump to General Protection Fault because of an invalid control register
    microcodeArray[6] = MicroOpcode.RelJumpImm(Register.INVALID, JumpCondition.ALWAYS, 250) // 256 - 6
    // check privilege mode
    microcodeArray[7] = MicroOpcode.ALUImmOp(MicroALUOpcode.CMP, Register.PRIV, 1, Register.SCRATCH_0, operationSizeOverrideValue = OperationSize.HALF)
    // jump to General Protection Fault if the privilege check fails
    microcodeArray[8] = MicroOpcode.RelJumpImm(Register.SCRATCH_0, JumpCondition.NOT_EQUAL, 248) // 256 - 8
    // jump into jump table
    microcodeArray[9] = MicroOpcode.ALUImmOp(MicroALUOpcode.SHL, Register.IMMEDIATE, 1, Register.SCRATCH_0, operationSizeOverrideValue = OperationSize.HALF) // operation size: half = <64 CRs, word = <16384 CRs, double = <2^30 CRs, quad = <2^63 CRs
    microcodeArray[10] = MicroOpcode.ALUImmOp(MicroALUOpcode.ADD, Register.SCRATCH_0, 1, Register.SCRATCH_0, operationSizeOverrideValue = OperationSize.HALF) // operation size: half = <64 CRs, word = <16384 CRs, double = <2^30 CRs, quad = <2^63 CRs
    microcodeArray[11] = MicroOpcode.RelJumpReg(Register.INVALID, JumpCondition.ALWAYS, Register.SCRATCH_0)
    
    // CPUID1
    microcodeArray[12] = MicroOpcode.LoadConst(Register.REGA, 0xE0FF)
    microcodeArray[13] = MicroOpcode.ALURegOp(MicroALUOpcode.SEXT, Register.REGA, Register.LOG_OPERATION_SIZE, Register.REGA, endOfInstruction = true, updateIPToNextInstruction = true)
    // CPUID2
    microcodeArray[14] = MicroOpcode.LoadConst(Register.REGA, 0xF)
    microcodeArray[15] = MicroOpcode.ALURegOp(MicroALUOpcode.SEXT, Register.REGA, Register.LOG_OPERATION_SIZE, Register.REGA, endOfInstruction = true, updateIPToNextInstruction = true)
    // FEAT
    microcodeArray[16] = MicroOpcode.LoadConst(Register.REGA, 0x9)
    microcodeArray[17] = MicroOpcode.ALURegOp(MicroALUOpcode.SEXT, Register.REGA, Register.LOG_OPERATION_SIZE, Register.REGA, endOfInstruction = true, updateIPToNextInstruction = true)
    // FLAGS
    microcodeArray[18] = MicroOpcode.ALURegOp(MicroALUOpcode.SEXT, Register.FLAG, Register.LOG_OPERATION_SIZE, Register.REGA, endOfInstruction = true, updateIPToNextInstruction = true)
    // INT_PC
    microcodeArray[20] = MicroOpcode.ALURegOp(MicroALUOpcode.SEXT, Register.INT_IP, Register.LOG_OPERATION_SIZE, Register.REGA, endOfInstruction = true, updateIPToNextInstruction = true)
    // INT_SP
    microcodeArray[22] = MicroOpcode.ALURegOp(MicroALUOpcode.SEXT, Register.INT_SP, Register.LOG_OPERATION_SIZE, Register.REGA, endOfInstruction = true, updateIPToNextInstruction = true)
    // INT_MASK
    microcodeArray[24] = MicroOpcode.ALURegOp(MicroALUOpcode.SEXT, Register.INT_MASK, Register.LOG_OPERATION_SIZE, Register.REGA, endOfInstruction = true, updateIPToNextInstruction = true)
    // INT_PENDING
    microcodeArray[26] = MicroOpcode.ALURegOp(MicroALUOpcode.SEXT, Register.INT_PENDING, Register.LOG_OPERATION_SIZE, Register.REGA, endOfInstruction = true, updateIPToNextInstruction = true)
    // INT_CAUSE
    microcodeArray[28] = MicroOpcode.ALURegOp(MicroALUOpcode.SEXT, Register.INT_CAUSE, Register.LOG_OPERATION_SIZE, Register.REGA, endOfInstruction = true, updateIPToNextInstruction = true)
    // INT_DATA
    microcodeArray[30] = MicroOpcode.ALURegOp(MicroALUOpcode.SEXT, Register.INT_DATA, Register.LOG_OPERATION_SIZE, Register.REGA, endOfInstruction = true, updateIPToNextInstruction = true)
    // INT_RET_PC
    microcodeArray[32] = MicroOpcode.ALURegOp(MicroALUOpcode.SEXT, Register.INT_RET_IP, Register.LOG_OPERATION_SIZE, Register.REGA, endOfInstruction = true, updateIPToNextInstruction = true)
    // INT_RET_SP
    microcodeArray[34] = MicroOpcode.ALURegOp(MicroALUOpcode.SEXT, Register.INT_RET_SP, Register.LOG_OPERATION_SIZE, Register.REGA, endOfInstruction = true, updateIPToNextInstruction = true)
    // PRIV
    microcodeArray[36] = MicroOpcode.ALURegOp(MicroALUOpcode.SEXT, Register.PRIV, Register.LOG_OPERATION_SIZE, Register.REGA, endOfInstruction = true, updateIPToNextInstruction = true)
    // INT_RET_PRIV
    microcodeArray[38] = MicroOpcode.ALURegOp(MicroALUOpcode.SEXT, Register.INT_RET_PRIV, Register.LOG_OPERATION_SIZE, Register.REGA, endOfInstruction = true, updateIPToNextInstruction = true)
    // CACHE_LINE_SIZE
    microcodeArray[40] = MicroOpcode.LoadConst(Register.REGA, 32)
    microcodeArray[41] = MicroOpcode.ALURegOp(MicroALUOpcode.SEXT, Register.REGA, Register.LOG_OPERATION_SIZE, Register.REGA, endOfInstruction = true, updateIPToNextInstruction = true)
    // NO_CACHE_START
    microcodeArray[42] = MicroOpcode.ALURegOp(MicroALUOpcode.SEXT, Register.NO_CACHE_START, Register.LOG_OPERATION_SIZE, Register.REGA, endOfInstruction = true, updateIPToNextInstruction = true)
    // NO_CACHE_END
    microcodeArray[44] = MicroOpcode.ALURegOp(MicroALUOpcode.SEXT, Register.NO_CACHE_END, Register.LOG_OPERATION_SIZE, Register.REGA, endOfInstruction = true, updateIPToNextInstruction = true)
    // ADDRESS_MODE
    microcodeArray[46] = MicroOpcode.ALURegOp(MicroALUOpcode.SEXT, Register.ADDRESS_MODE, Register.LOG_OPERATION_SIZE, Register.REGA, endOfInstruction = true, updateIPToNextInstruction = true)
    
    return microcodeArray
}

fun writeCROpcode(): Array<MicroOpcode> {
    val microcodeArray = Array<MicroOpcode>(128) {MicroOpcode.NOP}
    
    microcodeArray[0] = MicroOpcode.ALUImmOp(MicroALUOpcode.CMP, Register.IMMEDIATE, 4, Register.SCRATCH_0, operationSizeOverrideValue = OperationSize.QUAD)
    microcodeArray[1] = MicroOpcode.RelJumpImm(Register.SCRATCH_0, JumpCondition.BELOW, 8) // skip privilege check
    microcodeArray[2] = MicroOpcode.ALUImmOp(MicroALUOpcode.CMP, Register.IMMEDIATE, 14, Register.SCRATCH_0, operationSizeOverrideValue = OperationSize.QUAD)
    microcodeArray[3] = MicroOpcode.RelJumpImm(Register.SCRATCH_0, JumpCondition.EQUAL, 6) // skip privilege check
    when(version) {
        Version.ETCA_E0FF_F_9 -> microcodeArray[4] = MicroOpcode.ALUImmOp(MicroALUOpcode.CMP, Register.IMMEDIATE, 16, Register.SCRATCH_0, operationSizeOverrideValue = OperationSize.QUAD)
        Version.ETCA_5E0FF_F_9 -> microcodeArray[4] = MicroOpcode.ALUImmOp(MicroALUOpcode.CMP, Register.IMMEDIATE, 17, Register.SCRATCH_0, operationSizeOverrideValue = OperationSize.QUAD)
    }
    microcodeArray[5] = MicroOpcode.RelJumpImm(Register.SCRATCH_0, JumpCondition.BELOW_EQUAL, 2) // privilege check jump
    // jump to General Protection Fault because of an invalid control register
    microcodeArray[6] = MicroOpcode.RelJumpImm(Register.INVALID, JumpCondition.ALWAYS, 250) // 256 - 6
    // check privilege mode
    microcodeArray[7] = MicroOpcode.ALUImmOp(MicroALUOpcode.CMP, Register.PRIV, 1, Register.SCRATCH_0, operationSizeOverrideValue = OperationSize.HALF)
    // jump to General Protection Fault if the privilege check fails
    microcodeArray[8] = MicroOpcode.RelJumpImm(Register.SCRATCH_0, JumpCondition.NOT_EQUAL, 248) // 256 - 8
    // jump into jump table
    microcodeArray[9] = MicroOpcode.ALUImmOp(MicroALUOpcode.ADD, Register.IMMEDIATE, 1, Register.SCRATCH_0, operationSizeOverrideValue = OperationSize.HALF) // operation size: half = <64 CRs, word = <16384 CRs, double = <2^30 CRs, quad = <2^63 CRs
    microcodeArray[10] = MicroOpcode.RelJumpReg(Register.INVALID, JumpCondition.ALWAYS, Register.SCRATCH_0)
    
    // CPUID1
    microcodeArray[11] = MicroOpcode.ALURegOp(MicroALUOpcode.SEXT, Register.INVALID, Register.LOG_OPERATION_SIZE, Register.INVALID, endOfInstruction = true, updateIPToNextInstruction = true)
    // CPUID2
    microcodeArray[12] = MicroOpcode.ALURegOp(MicroALUOpcode.SEXT, Register.INVALID, Register.LOG_OPERATION_SIZE, Register.INVALID, endOfInstruction = true, updateIPToNextInstruction = true)
    // FEAT
    microcodeArray[13] = MicroOpcode.ALURegOp(MicroALUOpcode.SEXT, Register.INVALID, Register.LOG_OPERATION_SIZE, Register.INVALID, endOfInstruction = true, updateIPToNextInstruction = true)
    // FLAGS
    microcodeArray[14] = MicroOpcode.ALURegOp(MicroALUOpcode.SEXT, Register.REGA, Register.LOG_OPERATION_SIZE, Register.FLAG, endOfInstruction = true, updateIPToNextInstruction = true)
    // INT_PC
    microcodeArray[15] = MicroOpcode.ALURegOp(MicroALUOpcode.SEXT, Register.REGA, Register.LOG_OPERATION_SIZE, Register.INT_IP, endOfInstruction = true, updateIPToNextInstruction = true)
    // INT_SP
    microcodeArray[16] = MicroOpcode.ALURegOp(MicroALUOpcode.SEXT, Register.REGA, Register.LOG_OPERATION_SIZE, Register.INT_SP, endOfInstruction = true, updateIPToNextInstruction = true)
    // INT_MASK
    microcodeArray[17] = MicroOpcode.ALURegOp(MicroALUOpcode.SEXT, Register.REGA, Register.LOG_OPERATION_SIZE, Register.INT_MASK, endOfInstruction = true, updateIPToNextInstruction = true)
    // INT_PENDING
    microcodeArray[18] = MicroOpcode.ALURegOp(MicroALUOpcode.SEXT, Register.INVALID, Register.LOG_OPERATION_SIZE, Register.INVALID, endOfInstruction = true, updateIPToNextInstruction = true)
    // INT_CAUSE
    microcodeArray[19] = MicroOpcode.ALURegOp(MicroALUOpcode.SEXT, Register.INVALID, Register.LOG_OPERATION_SIZE, Register.INVALID, endOfInstruction = true, updateIPToNextInstruction = true)
    // INT_DATA
    microcodeArray[20] = MicroOpcode.ALURegOp(MicroALUOpcode.SEXT, Register.INVALID, Register.LOG_OPERATION_SIZE, Register.INVALID, endOfInstruction = true, updateIPToNextInstruction = true)
    // INT_RET_PC
    microcodeArray[21] = MicroOpcode.ALURegOp(MicroALUOpcode.SEXT, Register.REGA, Register.LOG_OPERATION_SIZE, Register.INT_RET_IP, endOfInstruction = true, updateIPToNextInstruction = true)
    // INT_RET_SP
    microcodeArray[22] = MicroOpcode.ALURegOp(MicroALUOpcode.SEXT, Register.REGA, Register.LOG_OPERATION_SIZE, Register.INT_RET_SP, endOfInstruction = true, updateIPToNextInstruction = true)
    // PRIV
    microcodeArray[23] = MicroOpcode.ALURegOp(MicroALUOpcode.SEXT, Register.REGA, Register.LOG_OPERATION_SIZE, Register.PRIV, endOfInstruction = true, updateIPToNextInstruction = true)
    // INT_RET_PRIV
    microcodeArray[24] = MicroOpcode.ALURegOp(MicroALUOpcode.SEXT, Register.REGA, Register.LOG_OPERATION_SIZE, Register.INT_RET_PRIV, endOfInstruction = true, updateIPToNextInstruction = true)
    // CACHE_LINE_SIZE
    microcodeArray[25] = MicroOpcode.ALURegOp(MicroALUOpcode.SEXT, Register.INVALID, Register.LOG_OPERATION_SIZE, Register.INVALID, endOfInstruction = true, updateIPToNextInstruction = true)
    // NO_CACHE_START
    microcodeArray[26] = MicroOpcode.ALURegOp(MicroALUOpcode.SEXT, Register.REGA, Register.LOG_OPERATION_SIZE, Register.NO_CACHE_START, endOfInstruction = true, updateIPToNextInstruction = true)
    // NO_CACHE_END
    microcodeArray[27] = MicroOpcode.ALURegOp(MicroALUOpcode.SEXT, Register.REGA, Register.LOG_OPERATION_SIZE, Register.NO_CACHE_END, endOfInstruction = true, updateIPToNextInstruction = true)
    // ADDRESS_MODE
    microcodeArray[28] = MicroOpcode.ALURegOp(MicroALUOpcode.SEXT, Register.REGA, Register.LOG_OPERATION_SIZE, Register.ADDRESS_MODE, endOfInstruction = true, updateIPToNextInstruction = true)
    
    return microcodeArray
}

fun interrupts(): Pair<Array<MicroOpcode>, List<MicroOpOffset>> {
    val microcodeArray = Array<MicroOpcode>(512) {MicroOpcode.NOP} // 32 micro-op sections
    val offsets = mutableListOf<MicroOpOffset>()
    
    offsets.add(MicroOpOffset("General Protection Fault", 0))
    // general protection fault
    microcodeArray[0] = MicroOpcode.ALUImmOp(MicroALUOpcode.TEST, Register.HANDLING_INTERRUPT, 1, Register.SCRATCH_0)
    microcodeArray[1] = MicroOpcode.RelJumpImm(Register.SCRATCH_0, JumpCondition.ZERO, 2)
    microcodeArray[2] = MicroOpcode.LoadConst(Register.SPECIAL_OPERATIONS, SPECIAL_OPERATION_RESET)
    microcodeArray[3] = MicroOpcode.ALUImmOp(MicroALUOpcode.OR, Register.INT_PENDING, 16, Register.INT_PENDING, operationSizeOverrideValue = OperationSize.QUAD)
    microcodeArray[4] = MicroOpcode.LoadConst(Register.INT_CAUSE, 4)
    microcodeArray[5] = MicroOpcode.LoadConst(Register.INT_DATA, -1)
    microcodeArray[6] = MicroOpcode.ALURegOp(MicroALUOpcode.SEXT, Register.IP, Register.ADDRESS_WIDTH, Register.INT_RET_IP)
    microcodeArray[7] = MicroOpcode.ALURegOp(MicroALUOpcode.SEXT, Register.SP, Register.ADDRESS_WIDTH, Register.INT_RET_SP)
    microcodeArray[8] = MicroOpcode.ALURegOp(MicroALUOpcode.SEXT, Register.INT_IP, Register.ADDRESS_WIDTH, Register.IP)
    microcodeArray[9] = MicroOpcode.ALURegOp(MicroALUOpcode.SEXT, Register.INT_SP, Register.ADDRESS_WIDTH, Register.SP)
    microcodeArray[10] = MicroOpcode.ALUImmOp(MicroALUOpcode.ZEXT, Register.PRIV, 0, Register.INT_RET_PRIV)
    microcodeArray[11] = MicroOpcode.LoadConst(Register.PRIV, 1)
    microcodeArray[12] = MicroOpcode.ALUImmOp(MicroALUOpcode.OR, Register.HANDLING_INTERRUPT, 1, Register.HANDLING_INTERRUPT, endOfInstruction = true, operationSizeOverrideValue = OperationSize.QUAD)
    
    offsets.add(MicroOpOffset("Reset", 32))
    // system reset
    microcodeArray[32] = MicroOpcode.LoadConst(Register.ADDRESS_MODE, 0)
    microcodeArray[33] = MicroOpcode.LoadConst(Register.IP, 0x8000)
    microcodeArray[34] = MicroOpcode.LoadConst(Register.INT_MASK, 0)
    microcodeArray[35] = MicroOpcode.LoadConst(Register.INT_PENDING, 0)
    microcodeArray[36] = MicroOpcode.LoadConst(Register.PRIV, 1)
    microcodeArray[37] = MicroOpcode.LoadConst(Register.NO_CACHE_START, 0)
    microcodeArray[38] = MicroOpcode.LoadConst(Register.NO_CACHE_END, -32)
    microcodeArray[39] = MicroOpcode.ALUImmOp(MicroALUOpcode.ZEXT, Register.NO_CACHE_START, 0, Register.HANDLING_INTERRUPT, endOfInstruction = true)
    
    offsets.add(MicroOpOffset("Illegal Instruction", 64))
    // illegal instruction
    microcodeArray[64] = MicroOpcode.ALUImmOp(MicroALUOpcode.TEST, Register.HANDLING_INTERRUPT, 1, Register.SCRATCH_0)
    microcodeArray[65] = MicroOpcode.RelJumpImm(Register.SCRATCH_0, JumpCondition.ZERO, 2)
    microcodeArray[66] = MicroOpcode.LoadConst(Register.SPECIAL_OPERATIONS, SPECIAL_OPERATION_RESET)
    microcodeArray[67] = MicroOpcode.ALUImmOp(MicroALUOpcode.OR, Register.INT_PENDING, 4, Register.INT_PENDING, operationSizeOverrideValue = OperationSize.QUAD)
    microcodeArray[68] = MicroOpcode.LoadConst(Register.INT_CAUSE, 2)
    microcodeArray[69] = MicroOpcode.LoadConst(Register.INT_DATA, -1)
    microcodeArray[70] = MicroOpcode.ALURegOp(MicroALUOpcode.SEXT, Register.IP, Register.ADDRESS_WIDTH, Register.INT_RET_IP)
    microcodeArray[71] = MicroOpcode.ALURegOp(MicroALUOpcode.SEXT, Register.SP, Register.ADDRESS_WIDTH, Register.INT_RET_SP)
    microcodeArray[72] = MicroOpcode.ALURegOp(MicroALUOpcode.SEXT, Register.INT_IP, Register.ADDRESS_WIDTH, Register.IP)
    microcodeArray[73] = MicroOpcode.ALURegOp(MicroALUOpcode.SEXT, Register.INT_SP, Register.ADDRESS_WIDTH, Register.SP)
    microcodeArray[74] = MicroOpcode.ALUImmOp(MicroALUOpcode.ZEXT, Register.PRIV, 0, Register.INT_RET_PRIV)
    microcodeArray[75] = MicroOpcode.LoadConst(Register.PRIV, 1)
    microcodeArray[76] = MicroOpcode.ALUImmOp(MicroALUOpcode.OR, Register.HANDLING_INTERRUPT, 1, Register.HANDLING_INTERRUPT, endOfInstruction = true, operationSizeOverrideValue = OperationSize.QUAD)
    
    offsets.add(MicroOpOffset("Timer", 96))
    // timer
    
    // Will never be triggered when an interrupt is already being handled
    // Pending CR is already set
    microcodeArray[96] = MicroOpcode.LoadConst(Register.INT_CAUSE, 1)
    microcodeArray[97] = MicroOpcode.LoadConst(Register.INT_DATA, -1)
    microcodeArray[98] = MicroOpcode.ALURegOp(MicroALUOpcode.SEXT, Register.IP, Register.ADDRESS_WIDTH, Register.INT_RET_IP)
    microcodeArray[99] = MicroOpcode.ALURegOp(MicroALUOpcode.SEXT, Register.SP, Register.ADDRESS_WIDTH, Register.INT_RET_SP)
    microcodeArray[100] = MicroOpcode.ALURegOp(MicroALUOpcode.SEXT, Register.INT_IP, Register.ADDRESS_WIDTH, Register.IP)
    microcodeArray[101] = MicroOpcode.ALURegOp(MicroALUOpcode.SEXT, Register.INT_SP, Register.ADDRESS_WIDTH, Register.SP)
    microcodeArray[102] = MicroOpcode.ALUImmOp(MicroALUOpcode.ZEXT, Register.PRIV, 0, Register.INT_RET_PRIV)
    microcodeArray[103] = MicroOpcode.LoadConst(Register.PRIV, 1)
    microcodeArray[104] = MicroOpcode.ALUImmOp(MicroALUOpcode.OR, Register.HANDLING_INTERRUPT, 1, Register.HANDLING_INTERRUPT, endOfInstruction = true, operationSizeOverrideValue = OperationSize.QUAD)
    
    offsets.add(MicroOpOffset("External", 128))
    // external
    
    // Will never be triggered when an interrupt is already being handled
    // Pending CR is already set
    microcodeArray[128] = MicroOpcode.LoadConst(Register.INT_CAUSE, 5)
    microcodeArray[129] = MicroOpcode.ALUImmOp(MicroALUOpcode.SEXT, Register.IO_BUS_IDENTIFIER, 3, Register.INT_DATA)
    microcodeArray[130] = MicroOpcode.ALURegOp(MicroALUOpcode.SEXT, Register.IP, Register.ADDRESS_WIDTH, Register.INT_RET_IP)
    microcodeArray[131] = MicroOpcode.ALURegOp(MicroALUOpcode.SEXT, Register.SP, Register.ADDRESS_WIDTH, Register.INT_RET_SP)
    microcodeArray[132] = MicroOpcode.ALURegOp(MicroALUOpcode.SEXT, Register.INT_IP, Register.ADDRESS_WIDTH, Register.IP)
    microcodeArray[133] = MicroOpcode.ALURegOp(MicroALUOpcode.SEXT, Register.INT_SP, Register.ADDRESS_WIDTH, Register.SP)
    microcodeArray[134] = MicroOpcode.ALUImmOp(MicroALUOpcode.ZEXT, Register.PRIV, 0, Register.INT_RET_PRIV)
    microcodeArray[135] = MicroOpcode.LoadConst(Register.PRIV, 1)
    microcodeArray[136] = MicroOpcode.ALUImmOp(MicroALUOpcode.OR, Register.HANDLING_INTERRUPT, 1, Register.HANDLING_INTERRUPT, endOfInstruction = true, operationSizeOverrideValue = OperationSize.QUAD)
    
    offsets.add(MicroOpOffset("Memory Alignment Error", 160))
    // memory alignment error
    microcodeArray[160] = MicroOpcode.ALUImmOp(MicroALUOpcode.TEST, Register.HANDLING_INTERRUPT, 1, Register.SCRATCH_1)
    microcodeArray[161] = MicroOpcode.RelJumpImm(Register.SCRATCH_1, JumpCondition.ZERO, 2)
    microcodeArray[162] = MicroOpcode.LoadConst(Register.SPECIAL_OPERATIONS, SPECIAL_OPERATION_RESET)
    microcodeArray[163] = MicroOpcode.ALUImmOp(MicroALUOpcode.OR, Register.INT_PENDING, 8, Register.INT_PENDING, operationSizeOverrideValue = OperationSize.QUAD)
    microcodeArray[164] = MicroOpcode.LoadConst(Register.INT_CAUSE, 3)
    microcodeArray[165] = MicroOpcode.ALUImmOp(MicroALUOpcode.SEXT, Register.SCRATCH_0, 1, Register.INT_DATA)
    microcodeArray[166] = MicroOpcode.ALURegOp(MicroALUOpcode.SEXT, Register.IP, Register.ADDRESS_WIDTH, Register.INT_RET_IP)
    microcodeArray[167] = MicroOpcode.ALURegOp(MicroALUOpcode.SEXT, Register.SP, Register.ADDRESS_WIDTH, Register.INT_RET_SP)
    microcodeArray[168] = MicroOpcode.ALURegOp(MicroALUOpcode.SEXT, Register.INT_IP, Register.ADDRESS_WIDTH, Register.IP)
    microcodeArray[169] = MicroOpcode.ALURegOp(MicroALUOpcode.SEXT, Register.INT_SP, Register.ADDRESS_WIDTH, Register.SP)
    microcodeArray[170] = MicroOpcode.ALUImmOp(MicroALUOpcode.ZEXT, Register.PRIV, 0, Register.INT_RET_PRIV)
    microcodeArray[171] = MicroOpcode.LoadConst(Register.PRIV, 1)
    microcodeArray[172] = MicroOpcode.ALUImmOp(MicroALUOpcode.OR, Register.HANDLING_INTERRUPT, 1, Register.HANDLING_INTERRUPT, endOfInstruction = true, operationSizeOverrideValue = OperationSize.QUAD)
    
    return Pair(microcodeArray, offsets)
}

fun memImmOpcode(opcode: Int, sx: Boolean, b: Boolean, i: Boolean): Pair<Array<MicroOpcode>, MicroOpOffset> {
    val microcodeArray = Array<MicroOpcode>(8) {MicroOpcode.NOP}
    val opcodeObj = Opcode[opcode] ?: return Pair(microcodeArray, MicroOpOffset.NONE)
    
    var index = 0
    microcodeArray[index++] = if(sx)
        MicroOpcode.ALURegOp(MicroALUOpcode.SHL, Register.REGX, Register.SCALE_X, Register.SCRATCH_1, operationSizeOverrideValue = OperationSize.ADDRESS_WIDTH)
    else
        MicroOpcode.LoadConst(Register.SCRATCH_1, 0)
    if(b)
        microcodeArray[index++] = MicroOpcode.ALURegOp(MicroALUOpcode.ADD, Register.SCRATCH_1, Register.REG_BASE, Register.SCRATCH_1, operationSizeOverrideValue = OperationSize.ADDRESS_WIDTH)
    if(i)
        microcodeArray[index++] = MicroOpcode.ALURegOp(MicroALUOpcode.ADD, Register.SCRATCH_1, Register.MEM_IMMEDIATE, Register.SCRATCH_1, operationSizeOverrideValue = OperationSize.ADDRESS_WIDTH)
    
    when(opcodeObj) {
        Opcode.MOVZ, Opcode.MOVS -> {
            microcodeArray[index] = MicroOpcode.ALURegOp(MicroALUOpcode.STORE, Register.SCRATCH_1, Register.IMMEDIATE, Register.INVALID, endOfInstruction = true, updateIPToNextInstruction = true)
        }
        Opcode.PUSH -> {
            microcodeArray[index++] = MicroOpcode.ALURegOp(MicroALUOpcode.LOAD, Register.SCRATCH_1, Register.INVALID, Register.SCRATCH_0, operationSizeOverrideValue = OperationSize.ADDRESS_WIDTH)
            microcodeArray[index++] = MicroOpcode.ALURegOp(MicroALUOpcode.SUB, Register.SCRATCH_0, Register.OPERATION_WIDTH, Register.SCRATCH_0, operationSizeOverrideValue = OperationSize.ADDRESS_WIDTH)
            microcodeArray[index++] = MicroOpcode.ALURegOp(MicroALUOpcode.STORE, Register.SCRATCH_0, Register.IMMEDIATE, Register.INVALID)
            microcodeArray[index] = MicroOpcode.ALURegOp(MicroALUOpcode.STORE, Register.SCRATCH_1, Register.SCRATCH_0, Register.INVALID, endOfInstruction = true, updateIPToNextInstruction = true, operationSizeOverrideValue = OperationSize.ADDRESS_WIDTH)
        }
        Opcode.RSUB, Opcode.RSBB -> {
            microcodeArray[index++] = MicroOpcode.ALURegOp(MicroALUOpcode.LOAD, Register.SCRATCH_1, Register.INVALID, Register.SCRATCH_0)
            microcodeArray[index++] = MicroOpcode.ALURegOp(opcodeObj.opcode, Register.IMMEDIATE, Register.SCRATCH_0, Register.SCRATCH_0, updateFlags = opcodeObj.flags)
            microcodeArray[index] = MicroOpcode.ALURegOp(MicroALUOpcode.STORE, Register.SCRATCH_1, Register.SCRATCH_0, Register.INVALID, endOfInstruction = true, updateIPToNextInstruction = true)
        }
        Opcode.LOAD -> {
            microcodeArray[index++] = MicroOpcode.ALURegOp(MicroALUOpcode.LOAD, Register.IMMEDIATE, Register.INVALID, Register.SCRATCH_0)
            microcodeArray[index] = MicroOpcode.ALURegOp(MicroALUOpcode.STORE, Register.SCRATCH_1, Register.SCRATCH_0, Register.INVALID, endOfInstruction = true, updateIPToNextInstruction = true)
        }
        Opcode.STORE -> {
            microcodeArray[index++] = MicroOpcode.ALURegOp(MicroALUOpcode.LOAD, Register.SCRATCH_1, Register.INVALID, Register.SCRATCH_0)
            microcodeArray[index] = MicroOpcode.ALURegOp(MicroALUOpcode.STORE, Register.IMMEDIATE, Register.SCRATCH_0, Register.INVALID, endOfInstruction = true, updateIPToNextInstruction = true)
        }
        Opcode.LEA, Opcode.POP, Opcode.READCR, Opcode.WRITECR, Opcode.SLO -> {
            // not a valid instruction
            return Pair(microcodeArray, MicroOpOffset.NONE)
        }
        else -> {
            microcodeArray[index++] = MicroOpcode.ALURegOp(MicroALUOpcode.LOAD, Register.SCRATCH_1, Register.INVALID, Register.SCRATCH_0)
            val result = MicroOpcode.ALURegOp(opcodeObj.opcode, Register.SCRATCH_0, Register.IMMEDIATE, Register.INVALID, updateFlags = opcodeObj.flags)
            
            if(opcodeObj.store)
                result.dest = Register.SCRATCH_0
            
            microcodeArray[index++] = result
            microcodeArray[index] = MicroOpcode.ALURegOp(MicroALUOpcode.STORE, Register.SCRATCH_1, Register.SCRATCH_0, Register.INVALID, endOfInstruction = true, updateIPToNextInstruction = true)
        }
    }
    
    return Pair(microcodeArray, MicroOpOffset(opcodeObj.name.lowercase(), 0))
}

fun regMemOpcode(opcode: Int, sx: Boolean, b: Boolean, i: Boolean): Pair<Array<MicroOpcode>, MicroOpOffset> {
    val microcodeArray = Array<MicroOpcode>(8) {MicroOpcode.NOP}
    val opcodeObj = Opcode[opcode] ?: return Pair(microcodeArray, MicroOpOffset.NONE)
    
    var index = 0
    microcodeArray[index++] = if(sx)
        MicroOpcode.ALURegOp(MicroALUOpcode.SHL, Register.REGX, Register.SCALE_X, Register.SCRATCH_1, operationSizeOverrideValue = OperationSize.ADDRESS_WIDTH)
    else
        MicroOpcode.LoadConst(Register.SCRATCH_1, 0)
    if(b)
        microcodeArray[index++] = MicroOpcode.ALURegOp(MicroALUOpcode.ADD, Register.SCRATCH_1, Register.REG_BASE, Register.SCRATCH_1, operationSizeOverrideValue = OperationSize.ADDRESS_WIDTH)
    if(i)
        microcodeArray[index++] = MicroOpcode.ALURegOp(MicroALUOpcode.ADD, Register.SCRATCH_1, Register.MEM_IMMEDIATE, Register.SCRATCH_1, operationSizeOverrideValue = OperationSize.ADDRESS_WIDTH)
    
    when(opcodeObj) {
        Opcode.MOVZ, Opcode.MOVS -> {
            microcodeArray[index] = MicroOpcode.ALURegOp(MicroALUOpcode.STORE, Register.REGB, Register.SCRATCH_1, Register.INVALID, endOfInstruction = true, updateIPToNextInstruction = true)
        }
        Opcode.PUSH -> {
            microcodeArray[index++] = MicroOpcode.ALURegOp(MicroALUOpcode.LOAD, Register.SCRATCH_1, Register.INVALID, Register.SCRATCH_0)
            microcodeArray[index++] = MicroOpcode.ALURegOp(MicroALUOpcode.SUB, Register.REGA, Register.OPERATION_WIDTH, Register.SCRATCH_1, operationSizeOverrideValue = OperationSize.ADDRESS_WIDTH)
            microcodeArray[index++] = MicroOpcode.ALURegOp(MicroALUOpcode.STORE, Register.SCRATCH_1, Register.SCRATCH_0, Register.INVALID)
            // already sign extended by above instructions
            microcodeArray[index] = MicroOpcode.ALUImmOp(MicroALUOpcode.SEXT, Register.SCRATCH_1, 3, Register.REGA, endOfInstruction = true, updateIPToNextInstruction = true)
        }
        Opcode.POP -> {
            microcodeArray[index++] = MicroOpcode.ALURegOp(MicroALUOpcode.LOAD, Register.SCRATCH_1, Register.INVALID, Register.SCRATCH_0, operationSizeOverrideValue = OperationSize.ADDRESS_WIDTH)
            microcodeArray[index++] = MicroOpcode.ALURegOp(MicroALUOpcode.LOAD, Register.SCRATCH_0, Register.INVALID, Register.REGA)
            microcodeArray[index++] = MicroOpcode.ALURegOp(MicroALUOpcode.ADD, Register.SCRATCH_0, Register.OPERATION_WIDTH, Register.SCRATCH_0, operationSizeOverrideValue = OperationSize.ADDRESS_WIDTH)
            microcodeArray[index] = MicroOpcode.ALURegOp(MicroALUOpcode.STORE, Register.SCRATCH_1, Register.SCRATCH_0, Register.INVALID, endOfInstruction = true, updateIPToNextInstruction = true, operationSizeOverrideValue = OperationSize.ADDRESS_WIDTH)
        }
        Opcode.RSUB, Opcode.RSBB -> {
            microcodeArray[index++] = MicroOpcode.ALURegOp(MicroALUOpcode.LOAD, Register.SCRATCH_1, Register.INVALID, Register.SCRATCH_0)
            microcodeArray[index] = MicroOpcode.ALURegOp(opcodeObj.opcode, Register.SCRATCH_0, Register.REGA, Register.REGA, updateFlags = opcodeObj.flags)
        }
        Opcode.LOAD -> {
            microcodeArray[index++] = MicroOpcode.ALURegOp(MicroALUOpcode.LOAD, Register.SCRATCH_1, Register.INVALID, Register.SCRATCH_0, operationSizeOverrideValue = OperationSize.ADDRESS_WIDTH)
            microcodeArray[index] = MicroOpcode.ALURegOp(MicroALUOpcode.LOAD, Register.SCRATCH_0, Register.INVALID, Register.REGA, endOfInstruction = true, updateIPToNextInstruction = true)
        }
        Opcode.STORE -> {
            microcodeArray[index++] = MicroOpcode.ALURegOp(MicroALUOpcode.LOAD, Register.SCRATCH_1, Register.INVALID, Register.SCRATCH_0, operationSizeOverrideValue = OperationSize.ADDRESS_WIDTH)
            microcodeArray[index] = MicroOpcode.ALURegOp(MicroALUOpcode.STORE, Register.SCRATCH_0, Register.REGA, Register.INVALID, endOfInstruction = true, updateIPToNextInstruction = true)
        }
        Opcode.LEA -> {
            microcodeArray[index] = MicroOpcode.ALURegOp(MicroALUOpcode.SEXT, Register.SCRATCH_1, Register.LOG_OPERATION_SIZE, Register.REGA, endOfInstruction = true, updateIPToNextInstruction = true)
        }
        Opcode.READCR, Opcode.WRITECR, Opcode.SLO -> {
            // not a valid instruction
            return Pair(microcodeArray, MicroOpOffset.NONE)
        }
        else -> {
            microcodeArray[index++] = MicroOpcode.ALURegOp(MicroALUOpcode.LOAD, Register.SCRATCH_1, Register.INVALID, Register.SCRATCH_0)
            val result = MicroOpcode.ALURegOp(opcodeObj.opcode, Register.REGA, Register.SCRATCH_0, Register.INVALID, updateFlags = opcodeObj.flags, endOfInstruction = true, updateIPToNextInstruction = true)
            
            if(opcodeObj.store)
                result.dest = Register.REGA
            
            microcodeArray[index] = result
        }
    }
    
    return Pair(microcodeArray, MicroOpOffset(opcodeObj.name.lowercase(), 0))
}

fun memRegOpcode(opcode: Int, sx: Boolean, b: Boolean, i: Boolean): Pair<Array<MicroOpcode>, MicroOpOffset> {
    val microcodeArray = Array<MicroOpcode>(8) {MicroOpcode.NOP}
    val opcodeObj = Opcode[opcode] ?: return Pair(microcodeArray, MicroOpOffset.NONE)
    
    var index = 0
    microcodeArray[index++] = if(sx)
        MicroOpcode.ALURegOp(MicroALUOpcode.SHL, Register.REGX, Register.SCALE_X, Register.SCRATCH_1, operationSizeOverrideValue = OperationSize.ADDRESS_WIDTH)
    else
        MicroOpcode.LoadConst(Register.SCRATCH_1, 0)
    if(b)
        microcodeArray[index++] = MicroOpcode.ALURegOp(MicroALUOpcode.ADD, Register.SCRATCH_1, Register.REG_BASE, Register.SCRATCH_1, operationSizeOverrideValue = OperationSize.ADDRESS_WIDTH)
    if(i)
        microcodeArray[index++] = MicroOpcode.ALURegOp(MicroALUOpcode.ADD, Register.SCRATCH_1, Register.MEM_IMMEDIATE, Register.SCRATCH_1, operationSizeOverrideValue = OperationSize.ADDRESS_WIDTH)
    
    when(opcodeObj) {
        Opcode.MOVZ, Opcode.MOVS -> {
            microcodeArray[index] = MicroOpcode.ALURegOp(MicroALUOpcode.STORE, Register.SCRATCH_1, Register.REGB, Register.INVALID, endOfInstruction = true, updateIPToNextInstruction = true)
        }
        Opcode.PUSH -> {
            microcodeArray[index++] = MicroOpcode.ALURegOp(MicroALUOpcode.LOAD, Register.SCRATCH_1, Register.INVALID, Register.SCRATCH_0, operationSizeOverrideValue = OperationSize.ADDRESS_WIDTH)
            microcodeArray[index++] = MicroOpcode.ALURegOp(MicroALUOpcode.SUB, Register.SCRATCH_0, Register.OPERATION_WIDTH, Register.SCRATCH_0, operationSizeOverrideValue = OperationSize.ADDRESS_WIDTH)
            microcodeArray[index++] = MicroOpcode.ALURegOp(MicroALUOpcode.STORE, Register.SCRATCH_0, Register.REGB, Register.INVALID)
            microcodeArray[index] = MicroOpcode.ALURegOp(MicroALUOpcode.STORE, Register.SCRATCH_1, Register.SCRATCH_0, Register.INVALID, endOfInstruction = true, updateIPToNextInstruction = true, operationSizeOverrideValue = OperationSize.ADDRESS_WIDTH)
        }
        Opcode.POP -> {
            microcodeArray[index++] = MicroOpcode.ALURegOp(MicroALUOpcode.LOAD, Register.REGB, Register.INVALID, Register.SCRATCH_0)
            microcodeArray[index++] = MicroOpcode.ALURegOp(MicroALUOpcode.STORE, Register.SCRATCH_1, Register.SCRATCH_0, Register.INVALID)
            microcodeArray[index] = MicroOpcode.ALURegOp(MicroALUOpcode.ADD, Register.REGB, Register.OPERATION_WIDTH, Register.REGB, endOfInstruction = true, updateIPToNextInstruction = true, operationSizeOverrideValue = OperationSize.ADDRESS_WIDTH)
        }
        Opcode.RSUB, Opcode.RSBB -> {
            microcodeArray[index++] = MicroOpcode.ALURegOp(MicroALUOpcode.LOAD, Register.SCRATCH_1, Register.INVALID, Register.SCRATCH_0)
            microcodeArray[index++] = MicroOpcode.ALURegOp(opcodeObj.opcode, Register.REGB, Register.SCRATCH_0, Register.SCRATCH_0, updateFlags = opcodeObj.flags)
            microcodeArray[index] = MicroOpcode.ALURegOp(MicroALUOpcode.STORE, Register.SCRATCH_1, Register.SCRATCH_0, Register.INVALID, endOfInstruction = true, updateIPToNextInstruction = true)
        }
        Opcode.LOAD -> {
            microcodeArray[index++] = MicroOpcode.ALURegOp(MicroALUOpcode.LOAD, Register.REGB, Register.INVALID, Register.SCRATCH_0)
            microcodeArray[index] = MicroOpcode.ALURegOp(MicroALUOpcode.STORE, Register.SCRATCH_1, Register.SCRATCH_0, Register.INVALID, endOfInstruction = true, updateIPToNextInstruction = true)
        }
        Opcode.STORE -> {
            microcodeArray[index++] = MicroOpcode.ALURegOp(MicroALUOpcode.LOAD, Register.SCRATCH_1, Register.INVALID, Register.SCRATCH_0)
            microcodeArray[index] = MicroOpcode.ALURegOp(MicroALUOpcode.STORE, Register.REGB, Register.SCRATCH_0, Register.INVALID, endOfInstruction = true, updateIPToNextInstruction = true)
        }
        Opcode.LEA, Opcode.READCR, Opcode.WRITECR, Opcode.SLO -> {
            // not a valid instruction
            return Pair(microcodeArray, MicroOpOffset.NONE)
        }
        else -> {
            microcodeArray[index++] = MicroOpcode.ALURegOp(MicroALUOpcode.LOAD, Register.SCRATCH_1, Register.INVALID, Register.SCRATCH_0)
            val result = MicroOpcode.ALURegOp(opcodeObj.opcode, Register.SCRATCH_0, Register.REGB, Register.INVALID, updateFlags = opcodeObj.flags)
            
            if(opcodeObj.store)
                result.dest = Register.SCRATCH_0
            
            microcodeArray[index++] = result
            microcodeArray[index] = MicroOpcode.ALURegOp(MicroALUOpcode.STORE, Register.SCRATCH_1, Register.SCRATCH_0, Register.INVALID, endOfInstruction = true, updateIPToNextInstruction = true)
        }
    }
    
    return Pair(microcodeArray, MicroOpOffset(opcodeObj.name.lowercase(), 0))
}

fun jumpOpcodes(): Pair<Array<MicroOpcode>, List<MicroOpOffset>> {
    val microcodeArray = Array<MicroOpcode>(384) {MicroOpcode.NOP} // 32 micro-ops per instruction
    val offsets = mutableListOf<MicroOpOffset>()
    
    offsets.add(MicroOpOffset("Absolute Immediate Jump Lower 8 Bits", 0))
    microcodeArray[0] = MicroOpcode.LoadConst(Register.SCRATCH_0, 0xFF.inv())
    microcodeArray[1] = MicroOpcode.ALURegOp(MicroALUOpcode.AND, Register.IP, Register.SCRATCH_0, Register.IP, operationSizeOverrideValue = OperationSize.ADDRESS_WIDTH)
    microcodeArray[2] = MicroOpcode.ALURegOp(MicroALUOpcode.OR, Register.IP, Register.IMMEDIATE, Register.IP, endOfInstruction = true, operationSizeOverrideValue = OperationSize.ADDRESS_WIDTH)
    
    offsets.add(MicroOpOffset("Absolute Immediate Call Lower 8 Bits", 32))
    microcodeArray[32] = MicroOpcode.ALURegOp(MicroALUOpcode.SEXT, Register.NEXT_INSTR_ADDR, Register.ADDRESS_WIDTH, Register.LN)
    microcodeArray[33] = MicroOpcode.LoadConst(Register.SCRATCH_0, 0xFF.inv())
    microcodeArray[34] = MicroOpcode.ALURegOp(MicroALUOpcode.AND, Register.IP, Register.SCRATCH_0, Register.IP, operationSizeOverrideValue = OperationSize.ADDRESS_WIDTH)
    microcodeArray[35] = MicroOpcode.ALURegOp(MicroALUOpcode.OR, Register.IP, Register.IMMEDIATE, Register.IP, endOfInstruction = true, operationSizeOverrideValue = OperationSize.ADDRESS_WIDTH)
    
    offsets.add(MicroOpOffset("Absolute Immediate Jump Lower 16 Bits", 64))
    when(version) {
        Version.ETCA_E0FF_F_9 -> {
            microcodeArray[64] = MicroOpcode.ALURegOp(MicroALUOpcode.SEXT, Register.IMMEDIATE, Register.ADDRESS_WIDTH, Register.IP, endOfInstruction = true)
        }
        Version.ETCA_5E0FF_F_9 -> {
            microcodeArray[64] = MicroOpcode.LoadConst(Register.SCRATCH_0, 0xFFFF.inv())
            microcodeArray[65] = MicroOpcode.ALURegOp(MicroALUOpcode.AND, Register.IP, Register.SCRATCH_0, Register.IP, operationSizeOverrideValue = OperationSize.ADDRESS_WIDTH)
            microcodeArray[66] = MicroOpcode.ALURegOp(MicroALUOpcode.OR, Register.IP, Register.IMMEDIATE, Register.IP, endOfInstruction = true, operationSizeOverrideValue = OperationSize.ADDRESS_WIDTH)
        }
    }
    
    offsets.add(MicroOpOffset("Absolute Immediate Call Lower 16 Bits", 96))
    microcodeArray[96] = MicroOpcode.ALURegOp(MicroALUOpcode.SEXT, Register.NEXT_INSTR_ADDR, Register.ADDRESS_WIDTH, Register.LN)
    when(version) {
        Version.ETCA_E0FF_F_9 -> {
            microcodeArray[97] = MicroOpcode.ALURegOp(MicroALUOpcode.SEXT, Register.IMMEDIATE, Register.ADDRESS_WIDTH, Register.IP, endOfInstruction = true)
        }
        Version.ETCA_5E0FF_F_9 -> {
            microcodeArray[97] = MicroOpcode.LoadConst(Register.SCRATCH_0, 0xFFFF.inv())
            microcodeArray[98] = MicroOpcode.ALURegOp(MicroALUOpcode.AND, Register.IP, Register.SCRATCH_0, Register.IP, operationSizeOverrideValue = OperationSize.ADDRESS_WIDTH)
            microcodeArray[99] = MicroOpcode.ALURegOp(MicroALUOpcode.OR, Register.IP, Register.IMMEDIATE, Register.IP, endOfInstruction = true, operationSizeOverrideValue = OperationSize.ADDRESS_WIDTH)
        }
    }
    
    offsets.add(MicroOpOffset("Absolute Immediate Jump Lower 32 Bits", 128))
    when(version) {
        Version.ETCA_E0FF_F_9 -> {
            // not valid with 16 bit addressing
        }
        Version.ETCA_5E0FF_F_9 -> {
            microcodeArray[128] = MicroOpcode.LoadConst(Register.SCRATCH_0, 0xFFFF_FFFF.inv().toInt())
            microcodeArray[129] = MicroOpcode.ALURegOp(MicroALUOpcode.AND, Register.IP, Register.SCRATCH_0, Register.IP, operationSizeOverrideValue = OperationSize.ADDRESS_WIDTH)
            microcodeArray[130] = MicroOpcode.ALURegOp(MicroALUOpcode.OR, Register.IP, Register.IMMEDIATE, Register.IP, endOfInstruction = true, operationSizeOverrideValue = OperationSize.ADDRESS_WIDTH)
        }
    }
    
    offsets.add(MicroOpOffset("Absolute Immediate Call Lower 32 Bits", 160))
    microcodeArray[160] = MicroOpcode.ALURegOp(MicroALUOpcode.SEXT, Register.NEXT_INSTR_ADDR, Register.ADDRESS_WIDTH, Register.LN)
    when(version) {
        Version.ETCA_E0FF_F_9 -> {
            // not valid with 16 bit addressing
        }
        Version.ETCA_5E0FF_F_9 -> {
            microcodeArray[161] = MicroOpcode.LoadConst(Register.SCRATCH_0, 0xFFFF_FFFF.inv().toInt())
            microcodeArray[162] = MicroOpcode.ALURegOp(MicroALUOpcode.AND, Register.IP, Register.SCRATCH_0, Register.IP, operationSizeOverrideValue = OperationSize.ADDRESS_WIDTH)
            microcodeArray[163] = MicroOpcode.ALURegOp(MicroALUOpcode.OR, Register.IP, Register.IMMEDIATE, Register.IP, endOfInstruction = true, operationSizeOverrideValue = OperationSize.ADDRESS_WIDTH)
        }
    }
    
    offsets.add(MicroOpOffset("Absolute Immediate Jump Lower 64 Bits", 192))
    when(version) {
        Version.ETCA_E0FF_F_9 -> {
            // not valid with 16 bit addressing
        }
        Version.ETCA_5E0FF_F_9 -> {
            microcodeArray[192] = MicroOpcode.ALURegOp(MicroALUOpcode.SEXT, Register.IMMEDIATE, Register.ADDRESS_WIDTH, Register.IP, endOfInstruction = true)
        }
    }
    
    offsets.add(MicroOpOffset("Absolute Immediate Call Lower 64 Bits", 224))
    microcodeArray[224] = MicroOpcode.ALURegOp(MicroALUOpcode.SEXT, Register.NEXT_INSTR_ADDR, Register.ADDRESS_WIDTH, Register.LN)
    when(version) {
        Version.ETCA_E0FF_F_9 -> {
            // not valid with 16 bit addressing
        }
        Version.ETCA_5E0FF_F_9 -> {
            microcodeArray[225] = MicroOpcode.ALURegOp(MicroALUOpcode.SEXT, Register.IMMEDIATE, Register.ADDRESS_WIDTH, Register.IP, endOfInstruction = true)
        }
    }
    
    offsets.add(MicroOpOffset("Relative Immediate Jump", 256))
    microcodeArray[256] = MicroOpcode.ALURegOp(MicroALUOpcode.ADD, Register.IP, Register.IMMEDIATE, Register.IP, endOfInstruction = true, operationSizeOverrideValue = OperationSize.ADDRESS_WIDTH)
    
    offsets.add(MicroOpOffset("Relative Immediate Call", 288))
    microcodeArray[288] = MicroOpcode.ALURegOp(MicroALUOpcode.SEXT, Register.NEXT_INSTR_ADDR, Register.ADDRESS_WIDTH, Register.LN)
    microcodeArray[289] = MicroOpcode.ALURegOp(MicroALUOpcode.ADD, Register.IP, Register.IMMEDIATE, Register.IP, endOfInstruction = true, operationSizeOverrideValue = OperationSize.ADDRESS_WIDTH)
    
    offsets.add(MicroOpOffset("Register Jump", 320))
    microcodeArray[320] = MicroOpcode.ALURegOp(MicroALUOpcode.SEXT, Register.REGA, Register.ADDRESS_WIDTH, Register.IP, endOfInstruction = true)
    
    offsets.add(MicroOpOffset("Register Call", 352))
    microcodeArray[352] = MicroOpcode.ALURegOp(MicroALUOpcode.SEXT, Register.NEXT_INSTR_ADDR, Register.ADDRESS_WIDTH, Register.LN)
    microcodeArray[353] = MicroOpcode.ALURegOp(MicroALUOpcode.SEXT, Register.REGA, Register.ADDRESS_WIDTH, Register.IP, endOfInstruction = true)
    
    return Pair(microcodeArray, offsets)
}

fun miscOpcodes(): Pair<Array<MicroOpcode>, List<MicroOpOffset>> {
    val microcodeArray = Array<MicroOpcode>(640) {MicroOpcode.NOP} // 32 micro-ops per instructions
    val offsets = mutableListOf<MicroOpOffset>()
    
    offsets.add(MicroOpOffset("IRet", 0))
    // iret
    microcodeArray[0] = MicroOpcode.ALUImmOp(MicroALUOpcode.TEST, Register.HANDLING_INTERRUPT, 1, Register.SCRATCH_0)
    microcodeArray[1] = MicroOpcode.RelJumpImm(Register.SCRATCH_0, JumpCondition.NOT_ZERO, 2)
    microcodeArray[2] = MicroOpcode.LoadConst(Register.SPECIAL_OPERATIONS, SPECIAL_OPERATION_RESET)
    microcodeArray[3] = MicroOpcode.LoadConst(Register.SCRATCH_0, 1)
    microcodeArray[4] = MicroOpcode.ALURegOp(MicroALUOpcode.SHL, Register.SCRATCH_0, Register.INT_CAUSE, Register.SCRATCH_0, operationSizeOverrideValue = OperationSize.QUAD)
    microcodeArray[5] = MicroOpcode.ALUImmOp(MicroALUOpcode.XOR, Register.SCRATCH_0, -1, Register.SCRATCH_0, operationSizeOverrideValue = OperationSize.QUAD)
    microcodeArray[6] = MicroOpcode.ALURegOp(MicroALUOpcode.AND, Register.INT_PENDING, Register.SCRATCH_0, Register.INT_PENDING, operationSizeOverrideValue = OperationSize.QUAD)
    microcodeArray[7] = MicroOpcode.ALURegOp(MicroALUOpcode.SEXT, Register.INT_RET_IP, Register.ADDRESS_WIDTH, Register.IP)
    microcodeArray[8] = MicroOpcode.ALURegOp(MicroALUOpcode.SEXT, Register.INT_RET_SP, Register.ADDRESS_WIDTH, Register.SP)
    microcodeArray[9] = MicroOpcode.ALUImmOp(MicroALUOpcode.ZEXT, Register.INT_RET_PRIV, 0, Register.PRIV)
    microcodeArray[10] = MicroOpcode.ALUImmOp(MicroALUOpcode.AND, Register.HANDLING_INTERRUPT, 0, Register.HANDLING_INTERRUPT, endOfInstruction = true, operationSizeOverrideValue = OperationSize.QUAD)
    
    offsets.add(MicroOpOffset("Halt", 32))
    // halt
    microcodeArray[32] = MicroOpcode.ALUImmOp(MicroALUOpcode.OR, Register.HALT_STATUS, 1, Register.HALT_STATUS, endOfInstruction = true, updateIPToNextInstruction = true)
    
    offsets.add(MicroOpOffset("Int", 64))
    // int
    microcodeArray[64] = MicroOpcode.ALUImmOp(MicroALUOpcode.TEST, Register.HANDLING_INTERRUPT, 1, Register.SCRATCH_0)
    microcodeArray[65] = MicroOpcode.RelJumpImm(Register.SCRATCH_0, JumpCondition.ZERO, 2)
    microcodeArray[66] = MicroOpcode.LoadConst(Register.SPECIAL_OPERATIONS, SPECIAL_OPERATION_RESET)
    microcodeArray[67] = MicroOpcode.ALUImmOp(MicroALUOpcode.OR, Register.INT_PENDING, 1, Register.INT_PENDING, operationSizeOverrideValue = OperationSize.QUAD)
    microcodeArray[68] = MicroOpcode.LoadConst(Register.INT_CAUSE, 0)
    microcodeArray[69] = MicroOpcode.ALUImmOp(MicroALUOpcode.ZEXT, Register.IMMEDIATE, 3, Register.INT_DATA)
    microcodeArray[70] = MicroOpcode.ALURegOp(MicroALUOpcode.SEXT, Register.IP, Register.ADDRESS_WIDTH, Register.INT_RET_IP)
    microcodeArray[71] = MicroOpcode.ALURegOp(MicroALUOpcode.SEXT, Register.SP, Register.ADDRESS_WIDTH, Register.INT_RET_SP)
    microcodeArray[72] = MicroOpcode.ALUImmOp(MicroALUOpcode.SEXT, Register.INT_IP, 1, Register.IP)
    microcodeArray[73] = MicroOpcode.ALUImmOp(MicroALUOpcode.SEXT, Register.INT_SP, 1, Register.SP)
    microcodeArray[74] = MicroOpcode.ALUImmOp(MicroALUOpcode.ZEXT, Register.PRIV, 0, Register.INT_RET_PRIV)
    microcodeArray[75] = MicroOpcode.LoadConst(Register.PRIV, 1)
    microcodeArray[76] = MicroOpcode.ALUImmOp(MicroALUOpcode.OR, Register.HANDLING_INTERRUPT, 1, Register.HANDLING_INTERRUPT, endOfInstruction = true, operationSizeOverrideValue = OperationSize.QUAD)
    
    offsets.add(MicroOpOffset("Data Prefetch", 128))
    // data prefetch
    microcodeArray[128] = MicroOpcode.ALUImmOp(MicroALUOpcode.SEXT, Register.REGB, 3, Register.SCRATCH_0)
    microcodeArray[129] = MicroOpcode.LoadConst(Register.SPECIAL_OPERATIONS, SPECIAL_OPERATION_DATA_PREFETCH_LINE)
    microcodeArray[130] = MicroOpcode.ALUImmOp(MicroALUOpcode.ADD, Register.INVALID, 0, Register.INVALID, endOfInstruction = true, updateIPToNextInstruction = true)
    
    offsets.add(MicroOpOffset("Instruction Prefetch", 160))
    // instruction prefetch
    microcodeArray[160] = MicroOpcode.ALUImmOp(MicroALUOpcode.SEXT, Register.REGB, 3, Register.SCRATCH_0)
    microcodeArray[161] = MicroOpcode.LoadConst(Register.SPECIAL_OPERATIONS, SPECIAL_OPERATION_INSTRUCTION_PREFETCH_LINE)
    microcodeArray[162] = MicroOpcode.ALUImmOp(MicroALUOpcode.ADD, Register.INVALID, 0, Register.INVALID, endOfInstruction = true, updateIPToNextInstruction = true)
    
    offsets.add(MicroOpOffset("Data Cache Flush", 192))
    // data cache flush
    microcodeArray[192] = MicroOpcode.ALUImmOp(MicroALUOpcode.SEXT, Register.REGB, 3, Register.SCRATCH_0)
    microcodeArray[193] = MicroOpcode.LoadConst(Register.SPECIAL_OPERATIONS, SPECIAL_OPERATION_DCACHE_FLUSH_LINE)
    microcodeArray[194] = MicroOpcode.ALUImmOp(MicroALUOpcode.ADD, Register.INVALID, 0, Register.INVALID, endOfInstruction = true, updateIPToNextInstruction = true)
    
    offsets.add(MicroOpOffset("Instruction Cache Invalidate", 224))
    // instruction cache invalidate
    microcodeArray[224] = MicroOpcode.ALUImmOp(MicroALUOpcode.SEXT, Register.REGB, 3, Register.SCRATCH_0)
    microcodeArray[225] = MicroOpcode.LoadConst(Register.SPECIAL_OPERATIONS, SPECIAL_OPERATION_ICACHE_INVALIDATE_LINE)
    microcodeArray[226] = MicroOpcode.ALUImmOp(MicroALUOpcode.ADD, Register.INVALID, 0, Register.INVALID, endOfInstruction = true, updateIPToNextInstruction = true)
    
    offsets.add(MicroOpOffset("Allocate Zero", 256))
    // allocate zero
    microcodeArray[256] = MicroOpcode.ALUImmOp(MicroALUOpcode.SEXT, Register.REGB, 3, Register.SCRATCH_0)
    microcodeArray[257] = MicroOpcode.LoadConst(Register.SPECIAL_OPERATIONS, SPECIAL_OPERATION_ALLOC_ZERO)
    microcodeArray[258] = MicroOpcode.ALUImmOp(MicroALUOpcode.ADD, Register.INVALID, 0, Register.INVALID, endOfInstruction = true, updateIPToNextInstruction = true)
    
    offsets.add(MicroOpOffset("Data Cache Invalidate", 288))
    // data cache invalidate
    microcodeArray[288] = MicroOpcode.ALUImmOp(MicroALUOpcode.SEXT, Register.REGB, 3, Register.SCRATCH_0)
    microcodeArray[289] = MicroOpcode.LoadConst(Register.SPECIAL_OPERATIONS, SPECIAL_OPERATION_DCACHE_INVALIDATE_LINE)
    microcodeArray[290] = MicroOpcode.ALUImmOp(MicroALUOpcode.ADD, Register.INVALID, 0, Register.INVALID, endOfInstruction = true, updateIPToNextInstruction = true)
    
    offsets.add(MicroOpOffset("Cache Flush All", 320))
    // cache flush all
    microcodeArray[320] = MicroOpcode.LoadConst(Register.SPECIAL_OPERATIONS, SPECIAL_OPERATION_FLUSH_ALL)
    microcodeArray[321] = MicroOpcode.ALUImmOp(MicroALUOpcode.ADD, Register.INVALID, 0, Register.INVALID, endOfInstruction = true, updateIPToNextInstruction = true)
    
    offsets.add(MicroOpOffset("Cache Invalidate All", 352))
    // cache invalidate all
    microcodeArray[352] = MicroOpcode.LoadConst(Register.SPECIAL_OPERATIONS, SPECIAL_OPERATION_INVALIDATE_ALL)
    microcodeArray[353] = MicroOpcode.ALUImmOp(MicroALUOpcode.ADD, Register.INVALID, 0, Register.INVALID, endOfInstruction = true, updateIPToNextInstruction = true)
    
    return Pair(microcodeArray, offsets)
}

fun Int.alu(): Int {
    if(this < -64 || this > 63)
        throw IllegalArgumentException("The ALU instructions can only handle 7 bit arguments between -64 and 63 inclusive")
    return ((this and 0x3F) shl ARGB_OFFSET) or ((this and 0x40) shl 25) or 1
}

fun Int.ldc(): Int {
    if(this < -8_388_608 || this > 8_388_607)
        throw IllegalArgumentException("The LDC instruction can only handle 24 bit arguments between -8_388_608 and 8_388_607 inclusive")
    return ((this and 0x7F) shl 2) or ((this and 0xFFFF80) shl 8) or 2
}

fun Int.jmpImm(): Int {
    if(this < -8192 || this > 8191)
        throw IllegalArgumentException("The JMP instruction can only handle 14 bit arguments between -8192 and 8191 inclusive")
    return ((this and 0x7F) shl 2) or ((this and 0x1F80) shl 8) or ((this and 0x2000) shl 18) or 3
}

fun Boolean.toInt(): Int = if(this) 1 else 0

fun IntBuffer.put(data: Array<MicroOpcode>) {
    put(data.map(MicroOpcode::value).toIntArray())
}