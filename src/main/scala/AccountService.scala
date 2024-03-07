import zio.http.*
import zio.http.endpoint.*
import zio.*
import zio.schema.*
import zio.http.codec.HttpCodec

trait AccountService:
  def createAccount(
      accountId: AccountId,
      delay: Option[Delay],
      die: Option[Die]
  ): ZIO[Any, AccountAlreadyExists | UnexpectedServerError, Unit]
  def deposit(
      accountId: AccountId,
      amount: Amount,
      delay: Option[Delay],
      die: Option[Die],
      transactionId: TransactionId
  ): ZIO[Any, AccountNotFound | DuplicateTransaction | UnexpectedServerError, Unit]
  def withdraw(
      accountId: AccountId,
      amount: Amount,
      delay: Option[Delay],
      die: Option[Die],
      transactionId: TransactionId
  ): ZIO[Any, AccountNotFound | DuplicateTransaction | InsufficientFunds | UnexpectedServerError, Unit]
  def balance(
      accountId: AccountId,
      delay: Option[Delay],
      die: Option[Die]
  ): ZIO[Any, AccountNotFound | UnexpectedServerError, Int]

object AccountService:
  val live = ZLayer.derive[AccountServiceImpl]

enum WithdrawResult:
  case Withdrew(state: State)
  case InsufficientFunds(state: State)

case class State(accounts: Map[AccountId, Amount] = Map.empty, transactions: Set[TransactionId] = Set.empty):
  def addAmount(
      accountId: AccountId,
      amount: Amount,
      transactionId: TransactionId
  ): Either[AccountNotFound | DuplicateTransaction, State] =
    if accounts.contains(accountId) && !transactions.contains(transactionId) then
      Right(
        copy(
          accounts = accounts.updated(accountId, Amount(accounts(accountId) + amount)),
          transactions = transactions + transactionId
        )
      )
    else if !accounts.contains(accountId) then Left(AccountNotFound(accountId))
    else Left(DuplicateTransaction(transactionId))

  def withdrawAmount(
      accountId: AccountId,
      amount: Amount,
      transactionId: TransactionId
  ): Either[AccountNotFound | DuplicateTransaction, WithdrawResult] =
    if accounts.contains(accountId) && !transactions.contains(transactionId) then
      val tId = transactions + transactionId
      if accounts(accountId) >= amount then
        Right(
          WithdrawResult.Withdrew(
            copy(
              accounts = accounts.updated(accountId, Amount(accounts(accountId) - amount)),
              transactions = tId
            )
          )
        )
      else Right(WithdrawResult.InsufficientFunds(copy(transactions = tId)))
    else if !accounts.contains(accountId) then Left(AccountNotFound(accountId))
    else Left(DuplicateTransaction(transactionId))

  def creeateAccount(accountId: AccountId): Either[AccountAlreadyExists, State] =
    if accounts.contains(accountId) then Left(AccountAlreadyExists(accountId))
    else Right(copy(accounts = accounts.updated(accountId, Amount(0))))

  def getBalance(accountId: AccountId): Either[AccountNotFound, Amount] =
    accounts.get(accountId).toRight(AccountNotFound(accountId))

class AccountServiceImpl(state: Ref.Synchronized[State]) extends AccountService:

  private def withQueries[R, E, A](delay: Option[Int], die: Option[Boolean])(
      zio: ZIO[R, E, A]
  ): ZIO[R, E | UnexpectedServerError, A] =
    for {
      _ <- ZIO.whenCase(delay) {
        case None    => ZIO.unit
        case Some(d) => ZIO.logWarning(s"adding delay of $d seconds") *> ZIO.sleep(d.seconds)
      }
      _ <- ZIO.whenCase(die) {
        case None        => ZIO.unit
        case Some(false) => ZIO.unit
        case Some(true) =>
          ZIO.logError(s"failing response intentionally as requested") *>
            ZIO.fail(UnexpectedServerError("server encountered an unexpected error"))
      }
      value <- zio
    } yield value

  def createAccount(
      accountId: AccountId,
      delay: Option[Delay],
      die: Option[Die]
  ) =
    withQueries(delay, die) {
      state.updateZIO { state =>
        ZIO.fromEither(state.creeateAccount(accountId)).tapError(e => ZIO.logError(e.toString)) <*
          ZIO.logInfo(s"Created account $accountId")
      }
    }

  def deposit(
      accountId: AccountId,
      amount: Amount,
      delay: Option[Delay],
      die: Option[Die],
      transactionId: TransactionId
  ) =
    withQueries(delay, die) {
      state.updateZIO { state =>
        ZIO.fromEither(state.addAmount(accountId, amount, transactionId)).tapError(e => ZIO.logError(e.toString)) <*
          ZIO.logInfo(s"Deposited $amount to account $accountId")
      }
    }

  def withdraw(
      accountId: AccountId,
      amount: Amount,
      delay: Option[Delay],
      die: Option[Die],
      transactionId: TransactionId
  ) =
    withQueries(delay, die) {
      state
        .modifyZIO { state =>
          ZIO
            .fromEither(state.withdrawAmount(accountId, amount, transactionId))
            .map {
              case WithdrawResult.Withdrew(newState)          => (true, newState)
              case WithdrawResult.InsufficientFunds(newState) => (false, newState)
            }
        }
        .filterOrFail(identity)(InsufficientFunds(accountId))
        .unit
        .tapError(e => ZIO.logError(e.toString)) <*
        ZIO.logInfo(s"Withdrew $amount from account $accountId")
    }

  def balance(accountId: AccountId, delay: Option[Delay], die: Option[Die]) =
    withQueries(delay, die) {
      state.get.flatMap(state => ZIO.fromEither(state.getBalance(accountId)).tapError(e => ZIO.logError(e.toString)))
    }
