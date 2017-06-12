package com.hypertino.facade

/**
  * Created by maqdev on 6/12/17.
  */
object FacadeApp extends App with Injectable {

  System.setProperty(FacadeConfigPaths.RAML_FILE, "raml-configs/perf-test.raml")
  implicit val injector = TestInjectors()
  val httpWorker = inject [HttpWorker]

  inject[Service].asInstanceOf[TestWsRestServiceApp].start {
    httpWorker.restRoutes.routes
  }
}
