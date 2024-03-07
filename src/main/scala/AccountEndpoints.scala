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

case class AccountAlreadyExists(
    account: Account,
    message: String = "this account id already exists"
) derives Schema

case class AccountNotFound(accountId: AccountId, message: String = "could not find this account") derives Schema
case class InsufficientFunds(
    transactionId: TransactionId,
    account: Account,
    message: String = "withdrawal failed due to insufficient funds"
) derives Schema

case class UnexpectedServerError(message: String) derives Schema

case class DuplicateTransaction(
    transactionId: TransactionId,
    account: Account,
    message: String = "this transaction is already executed"
) derives Schema

object AccountEndpoints:

  private val accountIdCodec = string("accountId").transform(AccountId.apply)(identity)
  private val amountCodec = int("amount").transform(Amount.apply)(identity)
  private val transactionIdCodec = string("transactionId").transform(TransactionId.apply)(identity)

  private val delayQuery = QueryCodec.queryInt("delay").transform(Delay.apply)(identity).optional ?? Doc.p(
    "if an integer value is provided, api will add delay of given seconds"
  )

  private val dieQuery = QueryCodec.queryBool("die").transform(Die.apply)(identity).optional ?? Doc.p(
    "if set to true, api will return 500 internal server error"
  )

  private val transactionHeader =
    HeaderCodec.name[String]("transactionId").transform(TransactionId.apply)(identity) ?? Doc.p(
      "transaction id should be unique across different transactions but same for retries of same transaction"
    )

  val createAccount =
    Endpoint(Method.POST / accountIdCodec)
      .query(delayQuery)
      .query(dieQuery)
      .out[Account]
      .outErrors(
        HttpCodec.error[AccountAlreadyExists](Status.Conflict) ?? Doc.p("this account id already exists"),
        HttpCodec.error[UnexpectedServerError](Status.InternalServerError) ?? Doc.p(
          "server encountered an unexpected error"
        )
      ) ?? (Doc.h1("new account") + Doc.p("creates a new account with given account id"))

  val deposit =
    Endpoint(Method.POST / accountIdCodec / "deposit" / amountCodec)
      .query(delayQuery)
      .query(dieQuery)
      .header(transactionHeader)
      .out[Account]
      .outErrors(
        HttpCodec.error[AccountNotFound](Status.NotFound) ?? Doc.p("could not find this account"),
        HttpCodec.error[UnexpectedServerError](Status.InternalServerError) ?? Doc.p(
          "server encountered an unexpected error"
        ),
        HttpCodec.error[DuplicateTransaction](Status.Conflict) ?? Doc.p("this transaction is already executed")
      ) ?? (Doc.h1("deposit money") + Doc.p("deposits given amount to given account id"))

  val withdraw = Endpoint(Method.POST / accountIdCodec / "withdraw" / amountCodec)
    .query(delayQuery)
    .query(dieQuery)
    .header(transactionHeader)
    .out[Account]
    .outErrors(
      HttpCodec.error[AccountNotFound](Status.NotFound) ?? Doc.p("could not find this account"),
      HttpCodec.error[InsufficientFunds](Status.BadRequest) ?? Doc.p("withdrawal failed due to insufficient funds"),
      HttpCodec.error[UnexpectedServerError](Status.InternalServerError) ?? Doc.p(
        "server encountered an unexpected error"
      ),
      HttpCodec.error[DuplicateTransaction](Status.Conflict) ?? Doc.p("this transaction is already executed")
    ) ?? (Doc.h1("withdraw money") + Doc.p("withdraws given amount from given account id"))

  val balance =
    Endpoint(Method.GET / accountIdCodec / "balance")
      .query(delayQuery)
      .query(dieQuery)
      .outErrors(
        HttpCodec.error[AccountNotFound](Status.NotFound) ?? Doc.p("could not find this account"),
        HttpCodec.error[UnexpectedServerError](Status.InternalServerError) ?? Doc.p(
          "server encountered an unexpected error"
        )
      )
      .out[Account] ?? (Doc.h1("account balance") + Doc.p("returns the balance of given account id"))

  val openApi = OpenAPIGen.fromEndpoints(
    "Bank Account API",
    "1.0.0",
    SchemaStyle.Inline,
    Seq(createAccount, deposit, withdraw, balance)
  )
