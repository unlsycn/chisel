package chisel3.simulator

import chisel3.{Data, RawModule}
import scala.util.Try
import svsim._

final object Simulator {
  trait BackendProcessor {
    def process[T <: Backend](
      backend: T
    )(
      tag:                                String,
      commonCompilationSettings:          CommonCompilationSettings,
      backendSpecificCompilationSettings: backend.CompilationSettings
    ): Unit
  }

  final case class BackendInvocationDigest[T](
    compilationStartTime: Long,
    compilationEndTime:   Long,
    outcome:              BackendInvocationOutcome[T]
  ) {
    def result = outcome match {
      case SimulationDigest(_, _, outcome) => outcome.get
      case CompilationFailed(error)        => throw error
    }
  }
  sealed trait BackendInvocationOutcome[T]
  final case class CompilationFailed[T](error: Throwable) extends BackendInvocationOutcome[T]

  final case class SimulationDigest[T](simulationStartTime: Long, simulationEndTime: Long, outcome: Try[T])
      extends BackendInvocationOutcome[T]

  private[simulator] final class WorkspaceCompiler[T, U](
    elaboratedModule:                 ElaboratedModule[T],
    workspace:                        Workspace,
    customSimulationWorkingDirectory: Option[String],
    verbose:                          Boolean,
    body:                             (SimulatedModule[T]) => U
  ) extends BackendProcessor {
    val results = scala.collection.mutable.Stack[BackendInvocationDigest[U]]()

    def process[T <: Backend](
      backend: T
    )(
      tag:                                String,
      commonCompilationSettings:          CommonCompilationSettings,
      backendSpecificCompilationSettings: backend.CompilationSettings
    ): Unit = {

      results.push({
        val compilationStartTime = System.nanoTime()
        try {
          val simulation = workspace
            .compile(backend)(
              tag,
              commonCompilationSettings.copy(
                // Append to the include directorires based on what the
                // workspace indicates is the path for primary sources.  This
                // ensures that `` `include `` directives can be resolved.
                includeDirs =
                  Some(commonCompilationSettings.includeDirs.getOrElse(Seq.empty) :+ workspace.primarySourcesPath)
              ),
              backendSpecificCompilationSettings,
              customSimulationWorkingDirectory,
              verbose
            )
          val compilationEndTime = System.nanoTime()
          val simulationOutcome = Try {
            simulation.runElaboratedModule(elaboratedModule = elaboratedModule)(body)
          }
          val simulationEndTime = System.nanoTime()
          BackendInvocationDigest(
            compilationStartTime = compilationStartTime,
            compilationEndTime = compilationEndTime,
            outcome = SimulationDigest(
              simulationStartTime = compilationEndTime,
              simulationEndTime = simulationEndTime,
              outcome = simulationOutcome
            )
          )
        } catch {
          case error: Throwable =>
            BackendInvocationDigest(
              compilationStartTime = compilationStartTime,
              compilationEndTime = System.nanoTime(),
              outcome = CompilationFailed(error)
            )
        }
      })
    }
  }
}

trait Simulator {

  def workspacePath: String
  def workingDirectoryPrefix = "workdir"
  def customSimulationWorkingDirectory: Option[String] = None
  def verbose:                          Boolean = false
  def firtoolArgs:                      Seq[String] = Seq()
  def commonCompilationSettings:        CommonCompilationSettings

  private[simulator] def processBackends(
    processor:                 Simulator.BackendProcessor,
    commonCompilationSettings: CommonCompilationSettings
  ): Unit
  private[simulator] def _simulate[T <: RawModule, U](
    module:       => T,
    layerControl: LayerControl.Type
  )(body: (SimulatedModule[T]) => U): Seq[Simulator.BackendInvocationDigest[U]] = {
    val workspace = new Workspace(path = workspacePath, workingDirectoryPrefix = workingDirectoryPrefix)
    workspace.reset()
    val elaboratedModule = workspace.elaborateGeneratedModule({ () => module }, firtoolArgs)
    workspace.generateAdditionalSources()
    val compiler = new Simulator.WorkspaceCompiler(
      elaboratedModule,
      workspace,
      customSimulationWorkingDirectory,
      verbose,
      { (module: SimulatedModule[T]) =>
        val outcome = body(module)
        module.completeSimulation()
        outcome
      }
    )

    processBackends(
      compiler,
      commonCompilationSettings.copy(
        verilogPreprocessorDefines =
          commonCompilationSettings.verilogPreprocessorDefines ++ layerControl.preprocessorDefines(
            elaboratedModule
          ),
        fileFilter = commonCompilationSettings.fileFilter.orElse(layerControl.shouldIncludeFile(elaboratedModule))
      )
    )
    compiler.results.toSeq
  }
}

trait MultiBackendSimulator extends Simulator {
  def processBackends(processor: Simulator.BackendProcessor): Unit

  def simulate[T <: RawModule, U](
    module:       => T,
    layerControl: LayerControl.Type = LayerControl.EnableAll
  )(body: (SimulatedModule[T]) => U): Seq[Simulator.BackendInvocationDigest[U]] = {
    _simulate(module, layerControl)(body)
  }
}

trait SingleBackendSimulator[T <: Backend] extends Simulator {
  val backend: T
  def tag:                                String
  def commonCompilationSettings:          CommonCompilationSettings
  def backendSpecificCompilationSettings: backend.CompilationSettings

  final def processBackends(
    processor:                 Simulator.BackendProcessor,
    commonCompilationSettings: CommonCompilationSettings
  ): Unit = {
    processor.process(backend)(tag, commonCompilationSettings, backendSpecificCompilationSettings)
  }

  def simulate[T <: RawModule, U](
    module:       => T,
    layerControl: LayerControl.Type = LayerControl.EnableAll
  )(body: (SimulatedModule[T]) => U): Simulator.BackendInvocationDigest[U] = {
    _simulate(module, layerControl)(body).head
  }

}
