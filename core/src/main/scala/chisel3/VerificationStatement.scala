// SPDX-License-Identifier: Apache-2.0

package chisel3

import chisel3.experimental.{BaseModule, SourceInfo}
import chisel3.internal.Builder.pushCommand
import chisel3.internal._
import chisel3.internal.firrtl.ir._
import chisel3.layer.block
import chisel3.layers
import chisel3.util.circt.IfElseFatalIntrinsic
import scala.annotation.nowarn
import scala.language.experimental.macros
import scala.reflect.macros.blackbox
import scala.reflect.macros.blackbox.Context

/** Scaladoc information for internal verification statement macros
  * that are used in objects assert, assume and cover.
  *
  * @groupdesc VerifPrintMacros
  *
  * <p>
  * '''These internal methods are not part of the public-facing API!'''
  * </p>
  * <br>
  *
  * @groupprio VerifPrintMacros 1001
  */
trait VerifPrintMacrosDoc

object assert extends VerifPrintMacrosDoc {

  /** Checks for a condition to be valid in the circuit at rising clock edge
    * when not in reset. If the condition evaluates to false, the circuit
    * simulation stops with an error.
    *
    * @param cond condition, assertion fires (simulation fails) when false
    * @param message optional format string to print when the assertion fires
    * @param data optional bits to print in the message formatting
    *
    * @note See [[printf.apply(fmt:String* printf]] for format string documentation
    * @note currently cannot be used in core Chisel / libraries because macro
    * defs need to be compiled first and the SBT project is not set up to do
    * that
    */
  // Macros currently can't take default arguments, so we need two functions to emulate defaults.
  def apply(
    cond:    Bool,
    message: String,
    data:    Bits*
  )(
    implicit sourceInfo: SourceInfo
  ): Assert = macro _applyMacroWithInterpolatorCheck

  /** Checks for a condition to be valid in the circuit at all times. If the
    * condition evaluates to false, the circuit simulation stops with an error.
    *
    * Does not fire when in reset (defined as the current implicit reset, e.g. as set by
    * the enclosing `withReset` or Module.reset.
    *
    * @param cond condition, assertion fires (simulation fails) on a rising clock edge when false and reset is not asserted
    * @param message optional chisel Printable type message
    *
    * @note See [[printf.apply(pable:chisel3\.Printable)*]] for documentation on printf using Printables
    * @note currently cannot be used in core Chisel / libraries because macro
    * defs need to be compiled first and the SBT project is not set up to do
    * that
    */
  def apply(
    cond:    Bool,
    message: Printable
  )(
    implicit sourceInfo: SourceInfo
  ): Assert = macro _applyMacroWithPrintableMessage

  def apply(cond: Bool)(implicit sourceInfo: SourceInfo): Assert =
    macro _applyMacroWithNoMessage

  import VerificationStatement._

  /** @group VerifPrintMacros */
  def _applyMacroWithInterpolatorCheck(
    c:          blackbox.Context
  )(cond:       c.Tree,
    message:    c.Tree,
    data:       c.Tree*
  )(sourceInfo: c.Tree
  ): c.Tree = {
    import c.universe._
    printf._checkFormatString(c)(message)
    val apply_impl_do = symbolOf[this.type].asClass.module.info.member(TermName("_applyWithSourceLinePrintable"))
    q"$apply_impl_do($cond, ${getLine(c)}, _root_.scala.Some(_root_.chisel3.Printable.pack($message, ..$data)))($sourceInfo)"
  }

  /** An elaboration-time assertion. Calls the built-in Scala assert function. */
  def apply(cond: Boolean, message: => String): Unit = Predef.assert(cond, message)

  /** An elaboration-time assertion. Calls the built-in Scala assert function. */
  def apply(cond: Boolean): Unit = Predef.assert(cond, "")

  /** Named class for assertions. */
  final class Assert private[chisel3] () extends VerificationStatement

  /** @group VerifPrintMacros */
  def _applyMacroWithStringMessage(
    c:          blackbox.Context
  )(cond:       c.Tree,
    message:    c.Tree,
    data:       c.Tree*
  )(sourceInfo: c.Tree
  ): c.Tree = {
    import c.universe._
    val apply_impl_do = symbolOf[this.type].asClass.module.info.member(TermName("_applyWithSourceLinePrintable"))
    q"$apply_impl_do($cond, ${getLine(c)},_root_.scala.Some(_root_.chisel3.Printable.pack($message,..$data)))($sourceInfo)"
  }

  /** @group VerifPrintMacros */
  def _applyMacroWithPrintableMessage(
    c:          blackbox.Context
  )(cond:       c.Tree,
    message:    c.Tree
  )(sourceInfo: c.Tree
  ): c.Tree = {
    import c.universe._
    val apply_impl_do = symbolOf[this.type].asClass.module.info.member(TermName("_applyWithSourceLinePrintable"))
    q"$apply_impl_do($cond, ${getLine(c)}, _root_.scala.Some($message))($sourceInfo)"
  }

  /** @group VerifPrintMacros */
  def _applyMacroWithNoMessage(
    c:          blackbox.Context
  )(cond:       c.Tree
  )(sourceInfo: c.Tree
  ): c.Tree = {
    import c.universe._
    val apply_impl_do = symbolOf[this.type].asClass.module.info.member(TermName("_applyWithSourceLinePrintable"))
    q"$apply_impl_do($cond, ${getLine(c)}, _root_.scala.None)($sourceInfo)"
  }

  private def emitIfElseFatalIntrinsic(
    clock:     Clock,
    predicate: Bool,
    enable:    Bool,
    format:    Printable
  )(
    implicit sourceInfo: SourceInfo
  ): Assert = {
    block(layers.Verification.Assert, skipIfAlreadyInBlock = true, skipIfLayersEnabled = true) {
      val id = Builder.forcedUserModule // It should be safe since we push commands anyway.
      IfElseFatalIntrinsic(id, format, "chisel3_builtin", clock, predicate, enable, format.unpackArgs: _*)(sourceInfo)
    }
    new Assert()
  }

  /** This will be removed in Chisel 3.6 in favor of the Printable version
    *
    * @group VerifPrintMacros
    */
  def _applyWithSourceLine(
    cond:    Bool,
    line:    SourceLineInfo,
    message: Option[String],
    data:    Bits*
  )(
    implicit sourceInfo: SourceInfo
  ): Assert = {
    val pable = formatFailureMessage("Assertion", line, cond, message.map(Printable.pack(_, data: _*)))
    emitIfElseFatalIntrinsic(Module.clock, cond, !Module.reset.asBool, pable)
  }

  /** @group VerifPrintMacros */
  def _applyWithSourceLinePrintable(
    cond:    Bool,
    line:    SourceLineInfo,
    message: Option[Printable]
  )(
    implicit sourceInfo: SourceInfo
  ): Assert = {
    message.foreach(Printable.checkScope(_))
    val pable = formatFailureMessage("Assertion", line, cond, message)
    emitIfElseFatalIntrinsic(Module.clock, cond, !Module.reset.asBool, pable)
  }
}

object assume extends VerifPrintMacrosDoc {

  /** Assumes a condition to be valid in the circuit at all times.
    * Acts like an assertion in simulation and imposes a declarative
    * assumption on the state explored by formal tools.
    *
    * Does not fire when in reset (defined as the encapsulating Module's
    * reset). If your definition of reset is not the encapsulating Module's
    * reset, you will need to gate this externally.
    *
    * May be called outside of a Module (like defined in a function), so
    * functions using assert make the standard Module assumptions (single clock
    * and single reset).
    *
    * @param cond condition, assertion fires (simulation fails) when false
    * @param message optional format string to print when the assertion fires
    * @param data optional bits to print in the message formatting
    *
    * @note See [[printf.apply(fmt:String* printf]] for format string documentation
    */
  // Macros currently can't take default arguments, so we need two functions to emulate defaults.
  def apply(
    cond:    Bool,
    message: String,
    data:    Bits*
  )(
    implicit sourceInfo: SourceInfo
  ): Assume = macro _applyMacroWithInterpolatorCheck

  /** Assumes a condition to be valid in the circuit at all times.
    * Acts like an assertion in simulation and imposes a declarative
    * assumption on the state explored by formal tools.
    *
    * Does not fire when in reset (defined as the encapsulating Module's
    * reset). If your definition of reset is not the encapsulating Module's
    * reset, you will need to gate this externally.
    *
    * @param cond condition, assertion fires (simulation fails) when false
    * @param message optional Printable type message when the assertion fires
    *
    * @note See [[printf.apply(pable:chisel3\.Printable)*]] for documentation on printf using Printables
    */
  def apply(
    cond:    Bool,
    message: Printable
  )(
    implicit sourceInfo: SourceInfo
  ): Assume = macro _applyMacroWithPrintableMessage

  def apply(cond: Bool)(implicit sourceInfo: SourceInfo): Assume =
    macro _applyMacroWithNoMessage

  /** An elaboration-time assumption. Calls the built-in Scala assume function. */
  def apply(cond: Boolean, message: => String): Unit = Predef.assume(cond, message)

  /** An elaboration-time assumption. Calls the built-in Scala assume function. */
  def apply(cond: Boolean): Unit = Predef.assume(cond, "")

  /** Named class for assumptions. */
  final class Assume private[chisel3] () extends VerificationStatement

  import VerificationStatement._

  /** @group VerifPrintMacros */
  def _applyMacroWithInterpolatorCheck(
    c:          blackbox.Context
  )(cond:       c.Tree,
    message:    c.Tree,
    data:       c.Tree*
  )(sourceInfo: c.Tree
  ): c.Tree = {
    import c.universe._
    printf._checkFormatString(c)(message)
    val apply_impl_do = symbolOf[this.type].asClass.module.info.member(TermName("_applyWithSourceLinePrintable"))
    q"$apply_impl_do($cond, ${getLine(c)}, _root_.scala.Some(_root_.chisel3.Printable.pack($message, ..$data)))($sourceInfo)"
  }

  /** @group VerifPrintMacros */
  def _applyMacroWithStringMessage(
    c:          blackbox.Context
  )(cond:       c.Tree,
    message:    c.Tree,
    data:       c.Tree*
  )(sourceInfo: c.Tree
  ): c.Tree = {
    import c.universe._
    val apply_impl_do = symbolOf[this.type].asClass.module.info.member(TermName("_applyWithSourceLinePrintable"))
    q"$apply_impl_do($cond, ${getLine(c)}, _root_.scala.Some(_root_.chisel3.Printable.pack($message, ..$data)))($sourceInfo)"
  }

  /** @group VerifPrintMacros */
  def _applyMacroWithPrintableMessage(
    c:          blackbox.Context
  )(cond:       c.Tree,
    message:    c.Tree
  )(sourceInfo: c.Tree
  ): c.Tree = {
    import c.universe._
    val apply_impl_do = symbolOf[this.type].asClass.module.info.member(TermName("_applyWithSourceLinePrintable"))
    q"$apply_impl_do($cond, ${getLine(c)}, _root_.scala.Some($message))($sourceInfo)"
  }

  /** @group VerifPrintMacros */
  def _applyMacroWithNoMessage(
    c:          blackbox.Context
  )(cond:       c.Tree
  )(sourceInfo: c.Tree
  ): c.Tree = {
    import c.universe._
    val apply_impl_do = symbolOf[this.type].asClass.module.info.member(TermName("_applyWithSourceLinePrintable"))
    q"$apply_impl_do($cond, ${getLine(c)}, _root_.scala.None)($sourceInfo)"
  }

  /** This will be removed in Chisel 3.6 in favor of the Printable version
    *
    * @group VerifPrintMacros
    */
  def _applyWithSourceLine(
    cond:    Bool,
    line:    SourceLineInfo,
    message: Option[String],
    data:    Bits*
  )(
    implicit sourceInfo: SourceInfo
  ): Assume = {
    val id = new Assume()
    block(layers.Verification.Assume, skipIfAlreadyInBlock = true, skipIfLayersEnabled = true) {
      when(!Module.reset.asBool) {
        val formattedMsg = formatFailureMessage("Assumption", line, cond, message.map(Printable.pack(_, data: _*)))
        Builder.pushCommand(Verification(id, Formal.Assume, sourceInfo, Module.clock.ref, cond.ref, formattedMsg))
      }
    }
    id
  }

  /** @group VerifPrintMacros */
  def _applyWithSourceLinePrintable(
    cond:    Bool,
    line:    SourceLineInfo,
    message: Option[Printable]
  )(
    implicit sourceInfo: SourceInfo
  ): Assume = {
    val id = new Assume()
    block(layers.Verification.Assume, skipIfAlreadyInBlock = true, skipIfLayersEnabled = true) {
      message.foreach(Printable.checkScope(_))
      when(!Module.reset.asBool) {
        val formattedMsg = formatFailureMessage("Assumption", line, cond, message)
        Builder.pushCommand(Verification(id, Formal.Assume, sourceInfo, Module.clock.ref, cond.ref, formattedMsg))
      }
    }
    id
  }
}

object cover extends VerifPrintMacrosDoc {

  /** Declares a condition to be covered.
    * At ever clock event, a counter is incremented iff the condition is active
    * and reset is inactive.
    *
    * Does not fire when in reset (defined as the encapsulating Module's
    * reset). If your definition of reset is not the encapsulating Module's
    * reset, you will need to gate this externally.
    *
    * @param cond condition that will be sampled on every clock tick
    * @param message a string describing the cover event
    */
  // Macros currently can't take default arguments, so we need two functions to emulate defaults.
  def apply(cond: Bool, message: String)(implicit sourceInfo: SourceInfo): Cover =
    macro _applyMacroWithMessage
  def apply(cond: Bool)(implicit sourceInfo: SourceInfo): Cover =
    macro _applyMacroWithNoMessage

  /** Named class for cover statements. */
  final class Cover private[chisel3] () extends VerificationStatement

  import VerificationStatement._

  /** @group VerifPrintMacros */
  def _applyMacroWithNoMessage(
    c:          blackbox.Context
  )(cond:       c.Tree
  )(sourceInfo: c.Tree
  ): c.Tree = {
    import c.universe._
    val apply_impl_do = symbolOf[this.type].asClass.module.info.member(TermName("_applyWithSourceLine"))
    q"$apply_impl_do($cond, ${getLine(c)}, _root_.scala.None)($sourceInfo)"
  }

  /** @group VerifPrintMacros */
  def _applyMacroWithMessage(
    c:          blackbox.Context
  )(cond:       c.Tree,
    message:    c.Tree
  )(sourceInfo: c.Tree
  ): c.Tree = {
    import c.universe._
    val apply_impl_do = symbolOf[this.type].asClass.module.info.member(TermName("_applyWithSourceLine"))
    q"$apply_impl_do($cond, ${getLine(c)}, _root_.scala.Some($message))($sourceInfo)"
  }

  /** @group VerifPrintMacros */
  def _applyWithSourceLine(
    cond:    Bool,
    line:    SourceLineInfo,
    message: Option[String]
  )(
    implicit sourceInfo: SourceInfo
  ): Cover = {
    val id = new Cover()
    block(layers.Verification.Cover, skipIfAlreadyInBlock = true, skipIfLayersEnabled = true) {
      when(!Module.reset.asBool) {
        Builder.pushCommand(Verification(id, Formal.Cover, sourceInfo, Module.clock.ref, cond.ref, ""))
      }
    }
    id
  }
}

object stop {

  /** Terminate execution, indicating success and printing a message.
    *
    * @param message a message describing why simulation was stopped
    */
  def apply(message: String)(implicit sourceInfo: SourceInfo): Stop = buildStopCommand(Some(PString(message)))

  /** Terminate execution, indicating success and printing a message.
    *
    * @param message a printable describing why simulation was stopped
    */
  def apply(message: Printable)(implicit sourceInfo: SourceInfo): Stop = buildStopCommand(Some(message))

  /** Terminate execution, indicating success.
    */
  def apply()(implicit sourceInfo: SourceInfo): Stop = buildStopCommand(None)

  /** Named class for [[stop]]s. */
  final class Stop private[chisel3] () extends VerificationStatement

  private def buildStopCommand(message: Option[Printable])(implicit sourceInfo: SourceInfo): Stop = {
    val stopId = new Stop()
    block(layers.Verification, skipIfAlreadyInBlock = true, skipIfLayersEnabled = true) {
      message.foreach(Printable.checkScope(_))
      when(!Module.reset.asBool) {
        message.foreach(printf.printfWithoutReset(_))
        pushCommand(Stop(stopId, sourceInfo, Builder.forcedClock.ref, 0))
      }
    }
    stopId
  }
}

/** Base class for all verification statements: Assert, Assume, Cover, Stop and Printf. */
abstract class VerificationStatement extends NamedComponent {
  _parent.foreach(_.addId(this))
}

/** Helper functions for common functionality required by stop, assert, assume or cover */
private object VerificationStatement {

  type SourceLineInfo = (String, Int)

  def getLine(c: blackbox.Context): SourceLineInfo = {
    val p = c.enclosingPosition
    (p.source.file.name, p.line): @nowarn // suppress, there's no clear replacement
  }

  def formatFailureMessage(
    kind:     String,
    lineInfo: SourceLineInfo,
    cond:     Bool,
    message:  Option[Printable]
  )(
    implicit sourceInfo: SourceInfo
  ): Printable = {
    val (filename, line) = lineInfo
    val lineMsg = s"$filename:$line".replaceAll("%", "%%")
    message match {
      case Some(msg) =>
        p"$kind failed: $msg\n"
      case None => p"$kind failed at $lineMsg\n"
    }
  }
}
