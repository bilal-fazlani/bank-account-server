import zio.http.*
import zio.http.endpoint.*
import zio.*
import zio.schema.*
import zio.http.codec.HttpCodec
import zio.http.endpoint.openapi.OpenAPIGen
import zio.http.endpoint.openapi.JsonSchema.SchemaStyle
import zio.http.codec.QueryCodec
import zio.http.codec.Doc

case class AccountAlreadyExists(accountId: String, message: String = "this account id already exists") derives Schema
case class AccountNotFound(accountId: String, message: String = "could not find this account") derives Schema
case class InsufficientFunds(accountId: String, message: String = "withdrawal failed due to insufficient funds")
    derives Schema

object AccountEndpoints:
  private val delayQuery = QueryCodec.queryInt("delay").optional ?? Doc.p(
    "if an integer value is provided, api will add delay of given seconds"
  )
  val createAccount =
    Endpoint(Method.POST / string("accountId"))
      .query(delayQuery)
      .out[Unit]
      .outError[AccountAlreadyExists](Status.Conflict) ?? Doc.h2("creates a new account")

  val deposit =
    Endpoint(Method.POST / string("accountId") / "deposit" / int("amount"))
      .query(delayQuery)
      .out[Unit]
      .outError[AccountNotFound](Status.NotFound) ?? Doc.h2("deposits money into given account")

  val withdraw = Endpoint(Method.POST / string("accountId") / "withdraw" / int("amount"))
    .query(delayQuery)
    .out[Unit]
    .outErrors(
      HttpCodec.error[AccountNotFound](Status.NotFound) ?? Doc.p("could not find this account"),
      HttpCodec.error[InsufficientFunds](Status.BadRequest) ?? Doc.p("withdrawal failed due to insufficient funds")
    ) ?? Doc.h2("withdraws money from given account")

  val balance =
    Endpoint(Method.GET / string("accountId") / "balance")
      .query(delayQuery)
      .outError[AccountNotFound](Status.NotFound)
      .out[Int] ?? Doc.h2("returns the balance of given account")

  val openApi = OpenAPIGen.fromEndpoints(
    "Bank Account API",
    "1.0.0",
    SchemaStyle.Inline,
    Seq(createAccount, deposit, withdraw, balance)
  )
