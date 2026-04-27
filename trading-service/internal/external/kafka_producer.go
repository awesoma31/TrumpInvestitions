package external

import (
	"context"
	"encoding/json"

	"github.com/segmentio/kafka-go"
	"github.com/vnikolaenko/trading-service/internal/domain"
)

type TradingEventProducer interface {
	ProduceTradingEvent(ctx context.Context, event *domain.TradingEvent) error
	Close() error
}

type kafkaProducer struct {
	writer *kafka.Writer
}

func NewKafkaProducer(brokers []string, topic string) (TradingEventProducer, error) {
	w := &kafka.Writer{
		Addr:     kafka.TCP(brokers...),
		Topic:    topic,
		Balancer: &kafka.LeastBytes{},
	}
	return &kafkaProducer{writer: w}, nil
}

func (p *kafkaProducer) ProduceTradingEvent(ctx context.Context, event *domain.TradingEvent) error {
	data, err := json.Marshal(event)
	if err != nil {
		return err
	}
	return p.writer.WriteMessages(ctx, kafka.Message{
		Key:   []byte(event.OrderID),
		Value: data,
	})
}

func (p *kafkaProducer) Close() error {
	return p.writer.Close()
}
