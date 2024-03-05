import zio.http.*
import zio.http.endpoint.*
import zio.*
import zio.schema.*
import zio.http.codec.HttpCodec

trait AccountService:
  def createAccount(accountId: String, delay: Option[Int]): ZIO[Any, AccountAlreadyExists, Unit]
  def deposit(accountId: String, amount: Int, delay: Option[Int]): ZIO[Any, AccountNotFound, Unit]
  def withdraw(accountId: String, amount: Int, delay: Option[Int]): ZIO[Any, AccountNotFound | InsufficientFunds, Unit]
  def balance(accountId: String, delay: Option[Int]): ZIO[Any, AccountNotFound, Int]

object AccountService:
  val live = ZLayer.derive[AccountServiceImpl]

class AccountServiceImpl(state: Ref.Synchronized[Map[String, Int]]) extends AccountService:

  private def withDelay[R, E, A](delay: Option[Int])(zio: ZIO[R, E, A]): ZIO[R, E, A] =
    delay.fold(zio)(d =>
      ZIO.logWarning(s"delaying response for $d seconds") *> ZIO.sleep(Duration.fromSeconds(d)) *> zio
    )

  def createAccount(accountId: String, delay: Option[Int]): ZIO[Any, AccountAlreadyExists, Unit] =
    withDelay(delay) {
      state.updateZIO { map =>
        if map.contains(accountId) then
          ZIO.logError(s"Account $accountId already exists") *> ZIO.fail(
            AccountAlreadyExists(accountId)
          )
        else ZIO.succeed(map.updated(accountId, 0)) <* ZIO.logInfo(s"Account $accountId created")
      }
    }

  def deposit(accountId: String, amount: Int, delay: Option[Int]): ZIO[Any, AccountNotFound, Unit] =
    withDelay(delay) {
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
      delay: Option[Int]
  ): ZIO[Any, AccountNotFound | InsufficientFunds, Unit] =
    withDelay(delay) {
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

  def balance(accountId: String, delay: Option[Int]): ZIO[Any, AccountNotFound, Int] =
    withDelay(delay) {
      state.get.flatMap(map =>
        if map.contains(accountId) then ZIO.succeed(map(accountId)) <* ZIO.logInfo(s"Balance of account $accountId")
        else ZIO.logError(s"Account $accountId not found") *> ZIO.fail(AccountNotFound(accountId))
      )
    }
