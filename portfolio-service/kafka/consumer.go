package kafka

import (
	"context"
	"encoding/json"
	"log"
	"strings"

	"github.com/awesoma31/portfolio-service/models"
	"github.com/awesoma31/portfolio-service/service"
	kafkago "github.com/segmentio/kafka-go"
)

type Consumer struct {
	reader *kafkago.Reader
	svc    *service.PortfolioService
}

func NewConsumer(brokers, topic, groupID string, svc *service.PortfolioService) *Consumer {
	r := kafkago.NewReader(kafkago.ReaderConfig{
		Brokers:  strings.Split(brokers, ","),
		Topic:    topic,
		GroupID:  groupID,
		MinBytes: 1,
		MaxBytes: 10e6,
	})
	return &Consumer{reader: r, svc: svc}
}

func (c *Consumer) Start(ctx context.Context) {
	log.Printf("Kafka consumer started, topic: %s", c.reader.Config().Topic)
	for {
		msg, err := c.reader.ReadMessage(ctx)
		if err != nil {
			if ctx.Err() != nil {
				return
			}
			log.Printf("kafka read error: %v", err)
			continue
		}

		var event models.TradingEvent
		if err := json.Unmarshal(msg.Value, &event); err != nil {
			log.Printf("kafka unmarshal error: %v", err)
			continue
		}

		if err := c.svc.ProcessTradingEvent(ctx, &event); err != nil {
			log.Printf("process event error: %v", err)
		}
	}
}

func (c *Consumer) Close() error {
	return c.reader.Close()
}
