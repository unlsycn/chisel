// SPDX-License-Identifier: Apache-2.0

package chiselTests.properties

import chisel3._
import chisel3.properties.Property
import chiselTests.{ChiselFlatSpec, MatchesAndOmits}
import circt.stage.ChiselStage

class PropertySpec extends ChiselFlatSpec with MatchesAndOmits {
  behavior.of("Property")

  it should "fail to compile with unsupported Property types" in {
    assertTypeError("""
      class MyThing
      val badProp = Property[MyThing]()
    """)
  }

  it should "fail to compile with unsupported Property literals" in {
    assertTypeError("""
      class MyThing
      val badProp = Property(new MyThing)
    """)
  }

  it should "support Int as a Property type" in {
    val chirrtl = ChiselStage.emitCHIRRTL(new RawModule {
      val intProp = IO(Input(Property[Int]()))
    })

    matchesAndOmits(chirrtl)(
      "input intProp : Integer"
    )()
  }

  it should "support Int as a Property literal" in {
    val chirrtl = ChiselStage.emitCHIRRTL(new RawModule {
      val propOut = IO(Output(Property[Int]()))
      propOut := Property(123)
    })

    matchesAndOmits(chirrtl)(
      "propassign propOut, Integer(123)"
    )()
  }

  it should "support Long as a Property type" in {
    val chirrtl = ChiselStage.emitCHIRRTL(new RawModule {
      val longProp = IO(Input(Property[Long]()))
    })

    matchesAndOmits(chirrtl)(
      "input longProp : Integer"
    )()
  }

  it should "support Long as a Property literal" in {
    val chirrtl = ChiselStage.emitCHIRRTL(new RawModule {
      val propOut = IO(Output(Property[Long]()))
      propOut := Property(123)
    })

    matchesAndOmits(chirrtl)(
      "propassign propOut, Integer(123)"
    )()
  }

  it should "support BigInt as a Property type" in {
    val chirrtl = ChiselStage.emitCHIRRTL(new RawModule {
      val bigIntProp = IO(Input(Property[BigInt]()))
    })

    matchesAndOmits(chirrtl)(
      "input bigIntProp : Integer"
    )()
  }

  it should "support BigInt as a Property literal" in {
    val chirrtl = ChiselStage.emitCHIRRTL(new RawModule {
      val propOut = IO(Output(Property[BigInt]()))
      propOut := Property(123)
    })

    matchesAndOmits(chirrtl)(
      "propassign propOut, Integer(123)"
    )()
  }

  it should "support String as a Property type" in {
    val chirrtl = ChiselStage.emitCHIRRTL(new RawModule {
      val stringProp = IO(Input(Property[String]()))
    })

    matchesAndOmits(chirrtl)(
      "input stringProp : String"
    )()
  }

  it should "support String as a Property literal" in {
    val chirrtl = ChiselStage.emitCHIRRTL(new RawModule {
      val propOut = IO(Output(Property[String]()))
      propOut := Property("fubar")
    })

    matchesAndOmits(chirrtl)(
      "propassign propOut, String(\"fubar\")"
    )()
  }

  it should "support connecting Property types of the same type" in {
    val chirrtl = ChiselStage.emitCHIRRTL(new RawModule {
      val propIn = IO(Input(Property[Int]()))
      val propOut = IO(Output(Property[Int]()))
      propOut := propIn
    })

    matchesAndOmits(chirrtl)(
      "propassign propOut, propIn"
    )()
  }

  it should "fail to compile when connecting Property types of different types" in {
    assertTypeError("""
      new RawModule {
        val propIn = IO(Input(Property[Int]()))
        val propOut = IO(Output(Property[BigInt]()))
        propOut := propIn
      }
    """)
  }
}