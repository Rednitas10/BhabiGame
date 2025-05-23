import Foundation

// Define a class Deck.
class Deck {
    // Add a property cards: [Card] initialized as an empty array.
    var cards: [Card] = []

    // Implement an initializer init() that populates the cards array with a
    // standard 52-card deck (one of each rank for each suit).
    // You can use Rank.allCases to help with this.
    init() {
        for suit in [Suit.spades, Suit.hearts, Suit.diamonds, Suit.clubs] {
            for rank in Rank.allCases {
                cards.append(Card(suit: suit, rank: rank))
            }
        }
    }

    // Implement a method shuffle() that randomizes the order of cards in the cards array.
    func shuffle() {
        cards.shuffle()
    }
}
