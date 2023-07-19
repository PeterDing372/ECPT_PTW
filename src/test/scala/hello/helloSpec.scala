package hello
import chisel3._
import chiseltest._
import org.scalatest.freespec.AnyFreeSpec

class helloSpec extends AnyFreeSpec with ChiselScalatestTester{
    "hello should properly pass through" in {

    test(new HelloModule ) { c =>
        println("Testing HelloModule:")
        c.io.in.poke(42.U)
        c.io.out.expect(42.U)
    }

    }
    
}

