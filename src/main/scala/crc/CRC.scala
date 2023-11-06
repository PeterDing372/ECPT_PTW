/*
 *   __   __     __  __     __         __
 *  /\ "-.\ \   /\ \/\ \   /\ \       /\ \
 *  \ \ \-.  \  \ \ \_\ \  \ \ \____  \ \ \____
 *   \ \_\\"\_\  \ \_____\  \ \_____\  \ \_____\
 *    \/_/ \/_/   \/_____/   \/_____/   \/_____/
 *   ______     ______       __     ______     ______     ______
 *  /\  __ \   /\  == \     /\ \   /\  ___\   /\  ___\   /\__  _\
 *  \ \ \/\ \  \ \  __<    _\_\ \  \ \  __\   \ \ \____  \/_/\ \/
 *   \ \_____\  \ \_____\ /\_____\  \ \_____\  \ \_____\    \ \_\
 *    \/_____/   \/_____/ \/_____/   \/_____/   \/_____/     \/_/
 *
 * https://joshbassett.info
 * https://twitter.com/nullobject
 * https://github.com/nullobject
 *
 * Copyright (c) 2021 Josh Bassett
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

package crc

import chisel3._


/**
 * Calculates the CRC for a stream of data.
 *
 * @param n The number of CRC bits to calculate.
 * @param g The generator polynomial.
 */
// class CRC(n: Int, g: Int) extends Module {
//   val io = IO(new Bundle {
//     /** Enable */
//     val en = Input(Bool())
//     /** Input */
//     val in = Input(UInt(1.W))
//     /** Output */
//     val out = Output(UInt(1.W))
//     /** Debug */
//     val debug = Output(UInt(n.W))
//   })

//   // Linear feedback shift register
//   val lfsr = Reg(Vec(n, Bool()))

//   // XOR the input bit with the last bit in the LFSR
//   val bit = Mux(io.en, io.in ^ lfsr.last, false.B)

//   // Load the first bit
//   lfsr(0) := bit

//   // Shift the LFSR bits
//   for (i <- 0 until (n - 2)) {
//     if ((g & (1 << i + 1)) != 0)
//       lfsr(i + 1) := lfsr(i) ^ bit
//     else
//       lfsr(i + 1) := lfsr(i)
//   }

//   // Output
//   io.out := Mux(io.en, io.in, lfsr.last)
//   io.debug := lfsr.asUInt

//   // Debug
//   if (sys.env.get("DEBUG").contains("1")) {
//     printf(p"CRC(data: ${lfsr} 0x${Hexadecimal(lfsr.asUInt)})\n")
//   }
// }

/**
 * Calculates the CRC for a stream of data.
 *
 * @param n The number of CRC bits to calculate.
 * @param g The generator polynomial.
 */
class CRC_hash(n: Int, g: Long) extends Module {
  val io = IO(new Bundle {

	/* 
		// Start (initiate trasmission)
		val start = Input(Bool())
	 */

	/** Enable */
    val en = Input(Bool())

    /** Input */
    val in = Input(UInt(1.W))
    /** Output */
    val out = Output(UInt(1.W))

    // val status = Ouput(UInt(2.W))
    /** Debug */
    val debug = Output(UInt(n.W))
  })
  println("----------------CRC_hash: -------------\n")

//   val idle :: working :: done :: Nil = Enum(3)

  /* status bit state machine*/
  
  


  // Linear feedback shift register
  // val lfsr = RegInit(Vec(n, Bool()))
  val lfsr = RegInit(VecInit((Seq.fill(n)(false.B))))
  println(s"The generator polynomial: ${g}\n")

  // XOR the input bit with the last bit in the LFSR
  val bit = Mux(io.en, io.in ^ lfsr.last, 0.U)
  val sel_last = Mux(io.en, lfsr.last, 0.U)

  // Load the first bit
  lfsr(0) := bit

  // Shift the LFSR bits
  for (i <- 0 until n - 1) {
    println(s"The ${i}th construct\n")

    if ((g & (1 << (i + 1))) != 0){
      println(s"between ${i+1} and ${i}\n")
      lfsr(i + 1) := lfsr(i) ^ sel_last 
    }
    else{
      lfsr(i + 1) := lfsr(i)
    }
      
  }

  // Output
  io.out := Mux(io.en, io.in, lfsr.last)
  io.debug := lfsr.asUInt

  // Debug
  if (sys.env.get("DEBUG").contains("1")) {
    printf(p"CRC(data: ${lfsr} 0x${Hexadecimal(lfsr.asUInt)})\n")
  }
}
