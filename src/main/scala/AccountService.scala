import zio.http.*
import zio.http.endpoint.*
import zio.*
import zio.schema.*
import zio.http.codec.HttpCodec

trait AccountService:
  def createAccount(
      accountId: String,
      delay: Option[Int],
      die: Option[Boolean]
  ): ZIO[Any, AccountAlreadyExists | UnexpectedServerError, Unit]
  def deposit(
      accountId: String,
      amount: Int,
      delay: Option[Int],
      die: Option[Boolean]
  ): ZIO[Any, AccountNotFound | UnexpectedServerError, Unit]
  def withdraw(
      accountId: String,
      amount: Int,
      delay: Option[Int],
      die: Option[Boolean]
  ): ZIO[Any, AccountNotFound | InsufficientFunds | UnexpectedServerError, Unit]
  def balance(
      accountId: String,
      delay: Option[Int],
      die: Option[Boolean]
  ): ZIO[Any, AccountNotFound | UnexpectedServerError, Int]

object AccountService:
  val live = ZLayer.derive[AccountServiceImpl]

class AccountServiceImpl(state: Ref.Synchronized[Map[String, Int]]) extends AccountService:

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
      accountId: String,
      delay: Option[Int],
      die: Option[Boolean]
  ) =
    withQueries(delay, die) {
      state.updateZIO { map =>
        if map.contains(accountId) then
          ZIO.logError(s"Account $accountId already exists") *> ZIO.fail(
            AccountAlreadyExists(accountId)
          )
        else ZIO.succeed(map.updated(accountId, 0)) <* ZIO.logInfo(s"Account $accountId created")
      }
    }

  def deposit(
      accountId: String,
      amount: Int,
      delay: Option[Int],
      die: Option[Boolean]
  ) =
    withQueries(delay, die) {
      state.updateZIO { map =>
        if map.contains(accountId) then
          ZIO.succeed(map.updated(accountId, map(accountId) + amount)) <* ZIO.logInfo(
            s"Deposited $amount to account $accountId"
          )
        else ZIO.logError(s"Account $accountId not found") *> ZIO.fail(AccountNotFound(accountId))
      }
    }

  def withdraw(
      accountId: String,
      amount: Int,
      delay: Option[Int],
      die: Option[Boolean]
  ) =
    withQueries(delay, die) {
      state.updateZIO { map =>
        if map.contains(accountId) then
          if map(accountId) >= amount then
            ZIO.succeed(map.updated(accountId, map(accountId) - amount)) <* ZIO.logInfo(
              s"Withdrew $amount from account $accountId"
            )
          else
            ZIO.logError(s"Account $accountId has insufficient funds") *> ZIO.fail(
              InsufficientFunds(accountId)
            )
        else
          ZIO.logError(s"Account $accountId has insufficient funds") *> ZIO.fail(
            AccountNotFound(accountId)
          )
      }
    }

  def balance(accountId: String, delay: Option[Int], die: Option[Boolean]) =
    withQueries(delay, die) {
      state.get.flatMap(map =>
        if map.contains(accountId) then ZIO.succeed(map(accountId)) <* ZIO.logInfo(s"Balance of account $accountId")
        else ZIO.logError(s"Account $accountId not found") *> ZIO.fail(AccountNotFound(accountId))
      )
    }
