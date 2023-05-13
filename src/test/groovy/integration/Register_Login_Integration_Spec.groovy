package integration

import app.App
import app.RootViewModel
import app.dev.ServerViewModel
import com.beust.jcommander.JCommander
import geb.spock.GebSpec
import spock.lang.Ignore

class Register_Login_Integration_Spec extends GebSpec
{
    def setupSpec() {
        System.setProperty("webdriver.gecko.driver","/home/gleethos/Documents/apps/driver/geckodriver")
    }

    def cleanupSpec() {}


    @Ignore
    def 'We can try to login, fail, and then register, succeed and finally enter the application!'() {
        given :
            var app = new App()
            JCommander.newBuilder()
                    .addObject(app)
                    .build()
                    .parse("--at", "saves/sqlite.db", "--port", "8080", "--start-server", "true")

            var runner = new Thread(app)

        when :
            runner.start()
            Thread.sleep(1000)
        and :
            go "https://localhost:8080"
        and :
            Thread.sleep(1000)
        then:
            true

        cleanup:
            runner.interrupt()
    }


    def 'Programmatically, we can try to login, fail, and then register, succeed and finally enter the application!'() {
        given :
            var app = new App()
            JCommander.newBuilder()
                    .addObject(app)
                    .build()
                    .parse("--at", "test_saves/sqlite.db", "--port", "4242", "--start-server", "false")

            RootViewModel root = app.createRootViewModel() // This is the root view model of the application
        when :
            var serverVM = root.serverViewModel()
            var databaseVM = root.dataBaseViewModel()
            var mainVM = root.mainViewModel()
        then :
            serverVM != null
            databaseVM != null
            mainVM != null
        and :
            serverVM.port().is("4242")
            serverVM.status().is(ServerViewModel.Status.OFFLINE)
            serverVM.portIsValid().is(true)
    }


}