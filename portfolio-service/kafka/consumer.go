package kafka

import (
	"context"
	"encoding/json"
	"log"
	"strings"
	"time"

	"github.com/awesoma31/portfolio-service/models"
	"github.com/awesoma31/portfolio-service/service"
	kafkago "github.com/segmentio/kafka-go"
)

type Consumer struct {
	brokers string
	topic   string
	groupID string
	svc     *service.PortfolioService
}

func NewConsumer(brokers, topic, groupID string, svc *service.PortfolioService) *Consumer {
	return &Consumer{brokers: brokers, topic: topic, groupID: groupID, svc: svc}
}

func (c *Consumer) newReader() *kafkago.Reader {
	return kafkago.NewReader(kafkago.ReaderConfig{
		Brokers:     strings.Split(c.brokers, ","),
		Topic:       c.topic,
		GroupID:     c.groupID,
		MinBytes:    1,
		MaxBytes:    10e6,
		StartOffset: kafkago.FirstOffset,
		Logger:      kafkago.LoggerFunc(func(msg string, args ...interface{}) { log.Printf("[kafka] "+msg, args...) }),
		ErrorLogger: kafkago.LoggerFunc(func(msg string, args ...interface{}) { log.Printf("[kafka-error] "+msg, args...) }),
	})
}

func (c *Consumer) waitForTopic(ctx context.Context) error {
	brokerList := strings.Split(c.brokers, ",")
	for {
		if ctx.Err() != nil {
			return ctx.Err()
		}
		conn, err := kafkago.Dial("tcp", brokerList[0])
		if err != nil {
			log.Printf("kafka dial error: %v, retrying in 2s...", err)
			time.Sleep(2 * time.Second)
			continue
		}
		partitions, err := conn.ReadPartitions(c.topic)
		conn.Close()
		if err == nil && len(partitions) > 0 {
			log.Printf("topic %s found with %d partitions", c.topic, len(partitions))
			return nil
		}
		log.Printf("topic %s not ready yet, retrying in 2s...", c.topic)
		time.Sleep(2 * time.Second)
	}
}

func (c *Consumer) Start(ctx context.Context) {
	log.Printf("Kafka consumer waiting for topic: %s", c.topic)
	if err := c.waitForTopic(ctx); err != nil {
		log.Printf("context cancelled while waiting for topic: %v", err)
		return
	}

	reader := c.newReader()
	defer reader.Close()

	log.Printf("Kafka consumer started, topic: %s", c.topic)
	for {
		msg, err := reader.ReadMessage(ctx)
		if err != nil {
			if ctx.Err() != nil {
				return
			}
			log.Printf("kafka read error: %v", err)
			continue
		}

		log.Printf("kafka message received: partition=%d offset=%d key=%s", msg.Partition, msg.Offset, string(msg.Key))

		var event models.TradingEvent
		if err := json.Unmarshal(msg.Value, &event); err != nil {
			log.Printf("kafka unmarshal error: %v", err)
			continue
		}

		log.Printf("processing event: type=%s user=%d symbol=%s", event.EventType, event.UserID, event.Symbol)
		if err := c.svc.ProcessTradingEvent(ctx, &event); err != nil {
			log.Printf("process event error: %v", err)
		} else {
			log.Printf("event processed successfully: type=%s", event.EventType)
		}
	}
}

func (c *Consumer) Close() error {
	return nil
}
