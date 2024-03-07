import zio.http.*
import zio.http.endpoint.*
import zio.*
import zio.schema.*
import zio.http.codec.HttpCodec
import zio.http.endpoint.openapi.OpenAPIGen
import zio.http.endpoint.openapi.JsonSchema.SchemaStyle
import zio.http.codec.QueryCodec
import zio.http.codec.Doc
import zio.http.codec.HeaderCodec
import neotype.zioschema.{given, *}

case class AccountAlreadyExists(accountId: AccountId, message: String = "this account id already exists") derives Schema
case class AccountNotFound(accountId: AccountId, message: String = "could not find this account") derives Schema
case class InsufficientFunds(accountId: AccountId, message: String = "withdrawal failed due to insufficient funds")
    derives Schema
case class UnexpectedServerError(message: String) derives Schema

object AccountEndpoints:
  private val delayQuery = QueryCodec.queryInt("delay").optional ?? Doc.p(
    "if an integer value is provided, api will add delay of given seconds"
  )

  private val dieQuery = QueryCodec.queryBool("die").optional ?? Doc.p(
    "if set to true, api will return 500 internal server error"
  )

  val createAccount =
    Endpoint(Method.POST / string("accountId"))
      .query(delayQuery)
      .query(dieQuery)
      .out[Unit]
      .outErrors(
        HttpCodec.error[AccountAlreadyExists](Status.Conflict) ?? Doc.p("this account id already exists"),
        HttpCodec.error[UnexpectedServerError](Status.InternalServerError) ?? Doc.p(
          "server encountered an unexpected error"
        )
      )

  val deposit =
    Endpoint(Method.POST / string("accountId") / "deposit" / int("amount"))
      .query(delayQuery)
      .query(dieQuery)
      .out[Unit]
      .outErrors(
        HttpCodec.error[AccountNotFound](Status.NotFound) ?? Doc.p("could not find this account"),
        HttpCodec.error[UnexpectedServerError](Status.InternalServerError) ?? Doc.p(
          "server encountered an unexpected error"
        )
      )

  val withdraw = Endpoint(Method.POST / string("accountId") / "withdraw" / int("amount"))
    .query(delayQuery)
    .query(dieQuery)
    .out[Unit]
    .outErrors(
      HttpCodec.error[AccountNotFound](Status.NotFound) ?? Doc.p("could not find this account"),
      HttpCodec.error[InsufficientFunds](Status.BadRequest) ?? Doc.p("withdrawal failed due to insufficient funds"),
      HttpCodec.error[UnexpectedServerError](Status.InternalServerError) ?? Doc.p(
        "server encountered an unexpected error"
      )
    ) ?? Doc.h2("withdraws money from given account")

  val balance =
    Endpoint(Method.GET / string("accountId") / "balance")
      .query(delayQuery)
      .query(dieQuery)
      .outErrors(
        HttpCodec.error[AccountNotFound](Status.NotFound) ?? Doc.p("could not find this account"),
        HttpCodec.error[UnexpectedServerError](Status.InternalServerError) ?? Doc.p(
          "server encountered an unexpected error"
        )
      )
      .out[Int] ?? Doc.h2("returns the balance of given account")

  val openApi = OpenAPIGen.fromEndpoints(
    "Bank Account API",
    "1.0.0",
    SchemaStyle.Inline,
    Seq(createAccount, deposit, withdraw, balance)
  )
