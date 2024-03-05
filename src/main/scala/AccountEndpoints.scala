import zio.http.*
import zio.http.endpoint.*
import zio.*
import zio.schema.*
import zio.http.codec.HttpCodec
import zio.http.endpoint.openapi.OpenAPIGen
import zio.http.endpoint.openapi.JsonSchema.SchemaStyle

case class AccountAlreadyExists(accountId: String, message: String = "this account id already exists") derives Schema
case class AccountNotFound(accountId: String, message: String = "could not find this account") derives Schema
case class InsufficientFunds(accountId: String, message: String = "withdrawal failed due to insufficient funds")
    derives Schema

object AccountEndpoints:
  val createAccount =
    Endpoint(Method.POST / string("accountId")).out[Unit].outError[AccountAlreadyExists](Status.Conflict)

  val deposit =
    Endpoint(Method.POST / string("accountId") / "deposit" / int("amount"))
      .out[Unit]
      .outError[AccountNotFound](Status.NotFound)

  val withdraw = Endpoint(Method.POST / string("accountId") / "withdraw" / int("amount"))
    .out[Unit]
    .outErrors(
      HttpCodec.error[AccountNotFound](Status.NotFound),
      HttpCodec.error[InsufficientFunds](Status.BadRequest)
    )

  val balance =
    Endpoint(Method.GET / string("accountId") / "balance").outError[AccountNotFound](Status.NotFound).out[Int]

  val openApi = OpenAPIGen.fromEndpoints(
    "Bank Account API",
    "1.0.0",
    SchemaStyle.Inline,
    Seq(createAccount, deposit, withdraw, balance)
  )
