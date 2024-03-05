import zio.http.Handler
import zio.http.endpoint.openapi.*
import zio.http.codec.PathCodec
import zio.http.endpoint.openapi.JsonSchema.SchemaStyle
import zio.http.Routes
import zio.http.Response
import zio.ZLayer

trait AccountRoutes:
  def routes: Routes[Any, Response]

object AccountRoutes: 
  val live = ZLayer.derive[AccountRoutesImpl]

class AccountRoutesImpl(accountService: AccountService) extends AccountRoutes:
  val createAccount = AccountEndpoints.createAccount.implement(Handler.fromFunctionZIO(accountService.createAccount))

  val deposit = AccountEndpoints.deposit.implement(Handler.fromFunctionZIO(accountService.deposit))

  val withdraw = AccountEndpoints.withdraw.implement(Handler.fromFunctionZIO(accountService.withdraw))

  val balance = AccountEndpoints.balance.implement(Handler.fromFunctionZIO(accountService.balance))

  val swaggerUI = SwaggerUI.routes(PathCodec.empty / "docs" / "openapi", AccountEndpoints.openApi)

  val routes: Routes[Any, Response] = Routes(createAccount, deposit, withdraw, balance) ++ swaggerUI
