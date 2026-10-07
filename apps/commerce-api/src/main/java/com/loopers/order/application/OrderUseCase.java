package com.loopers.order.application;

import com.loopers.order.domain.Order;
import com.loopers.order.domain.OrderConfirmService;
import com.loopers.order.domain.OrderItem;
import com.loopers.order.domain.OrderRepository;
import com.loopers.product.domain.Product;
import com.loopers.product.domain.ProductRepository;
import com.loopers.product.domain.Stock;
import com.loopers.product.domain.StockRepository;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorCode;
import com.loopers.support.page.PageResult;
import com.loopers.support.transaction.TransactionRetryExecutor;
import com.loopers.user.domain.User;
import com.loopers.user.domain.UserRepository;
import com.loopers.user.domain.Point;
import com.loopers.user.domain.PointRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.OptimisticLockException;
import org.springframework.stereotype.Service;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.transaction.annotation.Transactional;

import java.time.ZonedDateTime;
import java.util.List;

@Service
public class OrderUseCase {

    private final OrderRepository orderRepository;
    private final ProductRepository productRepository;
    private final UserRepository userRepository;
    private final PointRepository pointRepository;
    private final StockRepository stockRepository;
    private final TransactionRetryExecutor transactionRetryExecutor;
    private final EntityManager entityManager;
    private final OrderConfirmService confirmService = new OrderConfirmService();

    public OrderUseCase(
        OrderRepository orderRepository,
        ProductRepository productRepository,
        UserRepository userRepository,
        PointRepository pointRepository,
        StockRepository stockRepository,
        TransactionRetryExecutor transactionRetryExecutor,
        EntityManager entityManager
    ) {
        this.orderRepository = orderRepository;
        this.productRepository = productRepository;
        this.userRepository = userRepository;
        this.pointRepository = pointRepository;
        this.stockRepository = stockRepository;
        this.transactionRetryExecutor = transactionRetryExecutor;
        this.entityManager = entityManager;
    }

    @Transactional
    public Order create(Long buyerId, List<ItemCommand> items) {
        requireUser(buyerId);
        List<ItemCommand> requestedItems = items == null ? List.of() : items;
        List<OrderItem> orderItems = requestedItems.stream()
            .map(command -> OrderItem.of(requireActiveProduct(command.productId()), command.quantity()))
            .toList();
        return orderRepository.save(new Order(buyerId, orderItems));
    }

    public Order confirm(Long buyerId, Long orderId) {
        try {
            return transactionRetryExecutor.execute(() -> confirmInCurrentTransaction(buyerId, orderId));
        } catch (OptimisticLockException | OptimisticLockingFailureException exception) {
            throw new CoreException(ErrorCode.ORDER_ALREADY_CONFIRMED);
        }
    }

    private Order confirmInCurrentTransaction(Long buyerId, Long orderId) {
        Order order = orderRepository.findForConfirmById(orderId)
            .orElseThrow(() -> new CoreException(ErrorCode.ORDER_NOT_FOUND));
        if (!order.isOwnedBy(buyerId)) {
            throw new CoreException(ErrorCode.ORDER_NOT_FOUND);
        }
        User buyer = userRepository.findById(order.getBuyerId())
            .orElseThrow(() -> new CoreException(ErrorCode.USER_NOT_IDENTIFIED));
        List<Product> products = order.getItems().stream()
            .map(OrderItem::productId)
            .distinct()
            .sorted()
            .map(productRepository::findForOrder)
            .flatMap(java.util.Optional::stream)
            .toList();
        List<Stock> stocks = products.stream().map(product -> stockRepository.findForOrderByProductId(product.getId())
            .orElseThrow(() -> new CoreException(ErrorCode.PRODUCT_NOT_AVAILABLE))).toList();
        Point point = pointRepository.findForOrderByUserId(order.getBuyerId())
            .orElseThrow(() -> new CoreException(ErrorCode.USER_NOT_IDENTIFIED));

        confirmService.confirm(buyerId, order, products, stocks, point, ZonedDateTime.now());
        for (int i = 0; i < stocks.size(); i++) {
            Stock stock = stocks.get(i);
            OrderItem item = order.getItems().stream().filter(x -> x.productId().equals(stock.getProductId())).findFirst().orElseThrow();
            stock.changeQuantity(stock.decrease(item.quantity()).quantity());
            stockRepository.save(stock);
        }
        point.changeBalance(point.pay(order.getTotalAmount()).balance());
        pointRepository.save(point);
        Order saved = orderRepository.save(order);
        entityManager.flush();
        return saved;
    }

    @Transactional(readOnly = true)
    public List<Order> findMine(Long buyerId, int page, int size) {
        return orderRepository.findAllByBuyerId(buyerId, page, size);
    }

    @Transactional(readOnly = true)
    public Order findMine(Long buyerId, Long orderId) {
        Order order = findOrder(orderId);
        if (!order.isOwnedBy(buyerId)) {
            throw new CoreException(ErrorCode.ORDER_NOT_FOUND);
        }
        return order;
    }

    @Transactional(readOnly = true)
    public PageResult<Order> findMinePage(Long buyerId, int page, int size) {
        return new PageResult<>(
            findMine(buyerId, page, size),
            page,
            size,
            orderRepository.countAllByBuyerId(buyerId)
        );
    }

    @Transactional(readOnly = true)
    public List<Order> findForAdmin(Long buyerId, int page, int size) {
        return orderRepository.findAll(buyerId, page, size);
    }

    @Transactional(readOnly = true)
    public PageResult<Order> findPageForAdmin(Long buyerId, int page, int size) {
        return new PageResult<>(
            findForAdmin(buyerId, page, size),
            page,
            size,
            orderRepository.countAll(buyerId)
        );
    }

    @Transactional(readOnly = true)
    public Order findForAdmin(Long orderId) {
        return findOrder(orderId);
    }

    private User requireUser(Long userId) {
        return userRepository.findById(userId)
            .orElseThrow(() -> new CoreException(ErrorCode.USER_NOT_IDENTIFIED));
    }

    private Product requireActiveProduct(Long productId) {
        Product product = productRepository.findById(productId)
            .orElseThrow(() -> new CoreException(ErrorCode.PRODUCT_NOT_FOUND));
        if (product.isDeleted()) {
            throw new CoreException(ErrorCode.PRODUCT_NOT_FOUND);
        }
        return product;
    }

    private Order findOrder(Long orderId) {
        return orderRepository.findById(orderId)
            .orElseThrow(() -> new CoreException(ErrorCode.ORDER_NOT_FOUND));
    }

    public record ItemCommand(Long productId, int quantity) {
    }
}
